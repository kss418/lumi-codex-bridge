"""Line-delimited JSON interface for a Java parent process."""
import json
import queue
import threading
import sys
from codex_client import CodexClient, CodexError, GenerationCancelled, validate_image


class ProtocolError(ValueError):
    def __init__(self, code, message):
        super().__init__(message)
        self.code = code


class StdioSession:
    def __init__(self, client, model=None, effort=None):
        self.client = client
        self.model = model
        self.effort = effort
        self.thread_id = None
        self.persona = ""
        self.stop = False

    def dispatch(self, method, params, cancel_event=None):
        if method == "shutdown":
            self.stop = True
            return {"stopping": True}
        if method == "status":
            account = self.client.get_account()
            return {"connected": True, "auth_type": (account.get("account") or {}).get("type"),
                    "thread_id": self.thread_id, "model": self.model, "effort": self.effort}
        if method == "model/list":
            return {"models": self.client.list_models()}
        if method != "chat":
            raise ProtocolError(-32601, f"Unknown method: {method}")
        text = params.get("text")
        if not isinstance(text, str) or not text.strip():
            raise ProtocolError(-32602, "params.text must be a non-empty string")
        image = params.get("image")
        if image is not None:
            validate_image(image)
        persona = params.get("persona", self.persona)
        if not isinstance(persona, str) or len(persona) > 20000:
            raise ProtocolError(-32602, "params.persona must be a string of at most 20000 characters")
        model = params.get("model", self.model)
        effort = params.get("effort", self.effort)
        for key, value in (("model", model), ("effort", effort)):
            if value is not None and (not isinstance(value, str) or not value.strip()):
                raise ProtocolError(-32602, f"params.{key} must be a non-empty string or null")
        if self.thread_id is not None and model != self.model:
            raise ProtocolError(-32602, "Model is fixed for this conversation. Restart the bridge to change it.")
        if self.thread_id is None or persona != self.persona:
            options = {"model": model}
            if persona:
                options["persona"] = persona
            self.thread_id = self.client.start_thread(**options)
            self.model = model
            self.persona = persona
        try:
            options = {"effort": effort}
            if cancel_event is not None:
                options["cancel_event"] = cancel_event
            if image is not None:
                options["image"] = image
            answer = self.client.send_message(self.thread_id, text, **options)
        except GenerationCancelled:
            return {"text": "", "cancelled": True, "thread_id": self.thread_id}
        self.effort = effort
        return {"text": answer, "thread_id": self.thread_id}


def serve_lines(session, source, sink, *, queue_size=64):
    """One Codex worker; input-thread cancellation bypasses the chat FIFO."""
    if queue_size < 1:
        raise ValueError("queue_size must be positive")
    pending = queue.Queue(maxsize=queue_size)
    stopped = threading.Event()
    registry = {}
    guard = threading.Lock()
    output_lock = threading.Lock()
    eof = object()

    def emit(message):
        with output_lock:
            sink.write(json.dumps(message, ensure_ascii=False) + "\n")
            sink.flush()

    def envelope(item):
        return (isinstance(item, dict) and isinstance(item.get("id"), (str, int))
                and not isinstance(item.get("id"), bool)
                and isinstance(item.get("method"), str)
                and isinstance(item.get("params", {}), dict))

    def read_input():
        try:
            for line in source:
                if stopped.is_set():
                    return
                try:
                    item = json.loads(line)
                except ValueError:
                    item = None
                valid = envelope(item)
                if valid and item["method"] == "cancel":
                    target = item.get("params", {}).get("request_id")
                    if isinstance(target, bool) or not isinstance(target, (str, int)):
                        emit({"id": item["id"], "error": {"code": -32602, "message": "params.request_id must identify a chat request"}})
                        continue
                    with guard:
                        token = registry.get(target)
                        if token is not None:
                            token.set()
                    emit({"id": item["id"], "result": {"requested": token is not None, "request_id": target}})
                    continue
                token = None
                if valid and item["method"] == "chat":
                    with guard:
                        duplicate = item["id"] in registry
                        if not duplicate:
                            token = threading.Event()
                            registry[item["id"]] = token
                    if duplicate:
                        emit({"id": item["id"], "error": {"code": -32600, "message": "Chat request ID is already in use"}})
                        continue
                try:
                    pending.put_nowait((line, token, item["id"] if token is not None else None))
                except queue.Full:
                    if token is not None:
                        with guard:
                            registry.pop(item["id"], None)
                    emit({"id": item.get("id") if valid else None, "error": {"code": -32001, "message": "Request queue full; request was not accepted"}})
                    continue
                if valid and item["method"] == "shutdown":
                    break
        except Exception as exc:
            while not stopped.is_set():
                try:
                    pending.put(exc, timeout=0.1)
                    break
                except queue.Full:
                    pass
        finally:
            while not stopped.is_set():
                try:
                    pending.put(eof, timeout=0.1)
                    break
                except queue.Full:
                    pass

    def queued_lines():
        while True:
            item = pending.get()
            if item is eof:
                return
            if isinstance(item, Exception):
                raise item
            line, token, request_id = item
            try:
                yield line, token
            finally:
                if token is not None:
                    with guard:
                        if registry.get(request_id) is token:
                            registry.pop(request_id)

    emit({"event": "ready", "protocol_version": 1})
    reader = threading.Thread(target=read_input, name="lumi-input", daemon=True)
    reader.start()
    try:
        return _serve_lines(session, queued_lines(), sink, emit=emit)
    finally:
        stopped.set()
        reader.join(timeout=1)


def _serve_lines(session, source, sink, *, emit=None):
    def default_emit(message):
        sink.write(json.dumps(message, ensure_ascii=False) + "\n")
        sink.flush()

    emit = emit or default_emit
    for entry in source:
        line, cancel_event = entry if isinstance(entry, tuple) else (entry, None)
        request_id = None
        try:
            try:
                request = json.loads(line)
            except json.JSONDecodeError as exc:
                raise ProtocolError(-32700, "Invalid JSON") from exc
            if not isinstance(request, dict):
                raise ProtocolError(-32600, "Request must be an object")
            candidate = request.get("id")
            if isinstance(candidate, bool) or not isinstance(candidate, (int, str)):
                raise ProtocolError(-32600, "id must be a string or integer")
            request_id = candidate
            method = request.get("method")
            params = request.get("params", {})
            if not isinstance(method, str) or not method:
                raise ProtocolError(-32600, "method must be a non-empty string")
            if not isinstance(params, dict):
                raise ProtocolError(-32602, "params must be an object")
            if cancel_event is not None and cancel_event.is_set():
                result = {"text": "", "cancelled": True, "thread_id": session.thread_id}
            else:
                result = session.dispatch(method, params, cancel_event=cancel_event)
            emit({"id": request_id, "result": result})
        except ProtocolError as exc:
            emit({"id": request_id, "error": {"code": exc.code, "message": str(exc)}})
        except ValueError as exc:
            emit({"id": request_id, "error": {"code": -32602, "message": str(exc)}})
        except (CodexError, OSError) as exc:
            emit({"id": request_id, "error": {"code": -32000, "message": str(exc)}})
        if session.stop:
            break
    return 0


def run_stdio(*, model=None, effort=None):
    try:
        with CodexClient() as client:
            account = client.get_account()
            if account.get("requiresOpenaiAuth") and not account.get("account"):
                raise CodexError("Login required. Run codex login first.")
            return serve_lines(StdioSession(client, model, effort), sys.stdin, sys.stdout)
    except (CodexError, OSError, ValueError) as exc:
        print(json.dumps({"event": "error", "error": {"code": -32000, "message": str(exc)}}, ensure_ascii=False), flush=True)
        return 1
    except KeyboardInterrupt:
        return 0

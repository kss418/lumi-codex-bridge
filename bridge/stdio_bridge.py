"""Line-delimited JSON interface for a Java parent process."""
import json
import queue
import threading
import sys
from codex_client import CodexClient, CodexError


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
        self.stop = False

    def dispatch(self, method, params):
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
        model = params.get("model", self.model)
        effort = params.get("effort", self.effort)
        for key, value in (("model", model), ("effort", effort)):
            if value is not None and (not isinstance(value, str) or not value.strip()):
                raise ProtocolError(-32602, f"params.{key} must be a non-empty string or null")
        if self.thread_id is not None and model != self.model:
            raise ProtocolError(-32602, "Model is fixed for this conversation. Restart the bridge to change it.")
        if self.thread_id is None:
            self.thread_id = self.client.start_thread(model=model)
            self.model = model
        answer = self.client.send_message(self.thread_id, text, effort=effort)
        self.effort = effort
        return {"text": answer, "thread_id": self.thread_id}


def serve_lines(session, source, sink, *, queue_size=64):
    """Receive ahead on one thread; only this caller executes Codex requests.

    A bounded FIFO applies backpressure instead of dropping accepted requests.
    shutdown drains earlier requests; it does not cancel an active generation.
    """
    if queue_size < 1:
        raise ValueError("queue_size must be positive")
    pending = queue.Queue(maxsize=queue_size)
    stopped = threading.Event()
    eof = object()

    def enqueue(value):
        while not stopped.is_set():
            try:
                pending.put(value, timeout=0.1)
                return True
            except queue.Full:
                continue
        return False

    def read_input():
        try:
            for line in source:
                if not enqueue(line):
                    return
                # Do not consume more input after a valid shutdown envelope.
                # Full parsing and all state changes remain on the worker side.
                try:
                    item = json.loads(line)
                    if (isinstance(item, dict)
                            and isinstance(item.get("id"), (str, int))
                            and not isinstance(item.get("id"), bool)
                            and item.get("method") == "shutdown"
                            and isinstance(item.get("params", {}), dict)):
                        break
                except (ValueError, TypeError):
                    pass
        except Exception as exc:
            enqueue(exc)
        finally:
            enqueue(eof)

    def queued_lines():
        while True:
            item = pending.get()
            if item is eof:
                return
            if isinstance(item, Exception):
                raise item
            yield item

    reader = threading.Thread(target=read_input, name="lumi-input", daemon=True)
    reader.start()
    try:
        return _serve_lines(session, queued_lines(), sink)
    finally:
        stopped.set()
        reader.join(timeout=1)


def _serve_lines(session, source, sink):
    def emit(message):
        sink.write(json.dumps(message, ensure_ascii=False) + "\n")
        sink.flush()

    emit({"event": "ready", "protocol_version": 1})
    for line in source:
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
            result = session.dispatch(method, params)
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

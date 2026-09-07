"""Minimal, single-conversation Codex App Server client (stdlib only)."""
from __future__ import annotations

from collections import deque
import json
import os
from pathlib import Path
import queue
import shutil
import subprocess
import sys
import threading
import time


class CodexError(RuntimeError):
    pass


def find_codex() -> str:
    override = os.environ.get("CODEX_EXECUTABLE")
    if override:
        if not Path(override).is_file():
            raise CodexError("CODEX_EXECUTABLE does not point to a file")
        return override
    native = shutil.which("codex.exe" if os.name == "nt" else "codex")
    if native:
        return native
    # npm on Windows exposes a .cmd wrapper; start the native binary directly
    # so closing this client also closes the server it owns.
    if os.name == "nt":
        root = Path(os.environ.get("APPDATA", "")) / "npm/node_modules/@openai/codex"
        candidates = sorted(root.glob("**/codex.exe"))
        if candidates:
            return str(candidates[0])
    raise CodexError("Codex executable not found. Set CODEX_EXECUTABLE to codex.exe.")


class CodexClient:
    """Synchronous API; one caller and one active turn at a time."""

    def __init__(self, *, executable: str | None = None, timeout: float = 120):
        self.executable = executable
        self.timeout = timeout
        self.process = None
        self._incoming = queue.Queue()
        self._events = deque()
        self._next_id = 0
        self._readers = []
        self._thread_models = {}
        self._models = None

    def start(self):
        if self.process is not None:
            raise CodexError("Client already started")
        self.process = subprocess.Popen(
            [self.executable or find_codex(), "app-server", "--listen", "stdio://"],
            stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
            text=True, encoding="utf-8", bufsize=1,
            creationflags=subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0,
        )
        for target in (self._read_stdout, self._read_stderr):
            thread = threading.Thread(target=target, daemon=True)
            self._readers.append(thread)
            thread.start()
        try:
            self.request("initialize", {"clientInfo": {"name": "lumi_codex_bridge", "version": "0.1.0"}})
            self._write({"method": "initialized", "params": {}})
        except BaseException:
            self.close()
            raise
        return self

    def _read_stdout(self):
        try:
            for line in self.process.stdout:
                try:
                    self._incoming.put(json.loads(line))
                except json.JSONDecodeError:
                    self._incoming.put(CodexError("Invalid JSON from Codex App Server"))
                    return
        finally:
            self._incoming.put(CodexError("Codex App Server disconnected"))

    def _read_stderr(self):
        for line in self.process.stderr:
            print("[codex] " + line.rstrip(), file=sys.stderr)

    def _write(self, message):
        if self.process is None or self.process.poll() is not None:
            raise CodexError("Codex App Server is not running")
        try:
            self.process.stdin.write(json.dumps(message, ensure_ascii=False) + "\n")
            self.process.stdin.flush()
        except (BrokenPipeError, OSError) as exc:
            raise CodexError("Cannot write to Codex App Server") from exc

    def _receive(self, deadline):
        remaining = deadline - time.monotonic()
        if remaining <= 0:
            self.close()
            raise CodexError("Codex request timed out; connection closed")
        try:
            message = self._incoming.get(timeout=remaining)
        except queue.Empty:
            self.close()
            raise CodexError("Codex request timed out; connection closed") from None
        if isinstance(message, Exception):
            raise message
        # This minimal chat client has no permission or tool UI.
        if "method" in message and "id" in message:
            self._write({"id": message["id"], "error": {"code": -32601,
                         "message": "Client does not support interactive tool requests"}})
        return message

    def request(self, method, params):
        self._next_id += 1
        request_id = self._next_id
        self._write({"id": request_id, "method": method, "params": params})
        deadline = time.monotonic() + self.timeout
        while True:
            message = self._receive(deadline)
            if "method" not in message and message.get("id") == request_id:
                if "error" in message:
                    raise CodexError(str(message["error"].get("message", message["error"])))
                return message.get("result", {})
            if "method" in message and "id" not in message:
                self._events.append(message)

    def get_account(self):
        return self.request("account/read", {})

    def list_models(self):
        """Fetch every model page; no model invocation or usage charge."""
        models = []
        cursor = None
        while True:
            params = {"cursor": cursor} if cursor else {}
            page = self.request("model/list", params)
            models.extend(page["data"])
            cursor = page.get("nextCursor")
            if not cursor:
                break
        self._models = models
        return models

    def _model_info(self, name):
        models = self._models if self._models is not None else self.list_models()
        for model in models:
            if name in (model["model"], model["id"]):
                return model
        raise ValueError(f"Unknown model: {name}. Use --list-models to see available models.")

    def start_thread(self, *, model=None):
        params = {
            "sandbox": "read-only", "approvalPolicy": "never", "ephemeral": True,
            "developerInstructions": "Respond conversationally in Korean using polite language. Do not use tools or read files. Keep replies brief.",
        }
        if model is not None:
            params["model"] = self._model_info(model)["model"]
        result = self.request("thread/start", params)
        thread_id = result["thread"]["id"]
        # The server resolves the user's configured default when model is omitted.
        self._thread_models[thread_id] = result["model"]
        return thread_id

    def send_message(self, thread_id, text, *, effort=None):
        if not text.strip():
            raise ValueError("Message cannot be empty")
        params = {
            "threadId": thread_id, "input": [{"type": "text", "text": text}],
        }
        if effort is not None:
            if thread_id not in self._thread_models:
                raise ValueError("Create the thread with this client before selecting effort.")
            info = self._model_info(self._thread_models[thread_id])
            supported = [entry["reasoningEffort"] for entry in info["supportedReasoningEfforts"]]
            if effort not in supported:
                raise ValueError(f"{info['model']} does not support effort '{effort}'. Supported: {', '.join(supported)}")
            params["effort"] = effort
        result = self.request("turn/start", params)
        turn_id = result["turn"]["id"]
        messages = {}
        deadline = time.monotonic() + self.timeout
        while True:
            event = self._events.popleft() if self._events else self._receive(deadline)
            params = event.get("params", {})
            if params.get("threadId") != thread_id:
                continue
            if event.get("method") == "item/completed" and params.get("turnId") == turn_id:
                item = params.get("item", {})
                if item.get("type") == "agentMessage":
                    messages[item["id"]] = item
            if event.get("method") == "turn/completed" and params.get("turn", {}).get("id") == turn_id:
                turn = params["turn"]
                if turn["status"] != "completed":
                    raise CodexError((turn.get("error") or {}).get("message", "Turn " + turn["status"]))
                for item in turn.get("items", []):
                    if item.get("type") == "agentMessage":
                        messages[item["id"]] = item
                final = [item for item in messages.values() if item.get("phase") == "final_answer"]
                return "\n".join(item.get("text", "") for item in (final or messages.values()))

    def close(self):
        process = self.process
        if process is None:
            return
        if process.poll() is None:
            try:
                process.stdin.close()
                process.wait(timeout=3)
            except (OSError, subprocess.TimeoutExpired):
                process.terminate()
                try:
                    process.wait(timeout=3)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait()
        for thread in self._readers:
            thread.join(timeout=1)
        for stream in (process.stdin, process.stdout, process.stderr):
            stream.close()

    def __enter__(self):
        return self.start()

    def __exit__(self, *_):
        self.close()

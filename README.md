# lumi-codex-bridge

Java 25 plugin + Python 3.12 bridge for Little LUMI and Codex.

## Development environment

- JDK: `.tools/jdk-25` (Eclipse Temurin 25, repository-local).
- Python: `.venv/bin/python.exe` (created from the existing MSYS2 Python 3.12).
- Python bridge runtime: standard library only; no pip dependencies required.
- Plugin compile dependencies: the installed Little LUMI `app/Shimeji-ee.jar` and `app/lib/*.jar`. These are referenced locally, not redistributed.
- Codex: existing CLI installation; account login is separate from environment setup.

Open a new VS Code terminal to apply the Java/Python paths. The local `.vscode/settings.json` selects the installed tools.

## Layout

- `plugin/src/main/java/`: Java plugin sources.
- `plugin/src/main/resources/`: plugin.json and META-INF/services registration.
- `bridge/`: Python bridge sources.
- `scripts/build-plugin.ps1`: compile Java sources and package the plugin JAR.
- `dist/`: local build output, excluded from Git.

After adding plugin sources, run `powershell -ExecutionPolicy Bypass -File scripts/build-plugin.ps1`.
The Python Codex client is implemented; Java plugin integration is not implemented yet.

## Minimal Codex client

```powershell
# Protocol + login check (no model generation)
.\.venv\bin\python.exe .\bridge\codex_client.py --check

# Interactive conversation; /quit exits
.\.venv\bin\python.exe .\bridge\codex_client.py

# Two messages in the same conversation
.\.venv\bin\python.exe .\bridge\codex_client.py --message "안녕하세요" --message "방금 제가 뭐라고 했나요?"
```

Requires an existing `codex login`. The client launches the native Codex App Server with hidden-window stdio, initializes the protocol, and creates one ephemeral thread. It uses the configured default model and your existing Codex usage allowance. Conversation persistence across restarts, login UI, and Java integration are not implemented yet.

Only one caller/turn at a time is supported. Final agent messages are collected from completion events. Interactive tool requests are unsupported; threads request read-only sandboxing and are instructed to chat without tools. A timeout closes the connection rather than retrying and possibly duplicating a turn.

Set `CODEX_EXECUTABLE` to the native Codex executable if automatic discovery fails. stdout uses UTF-8; Codex diagnostic logs go to stderr. The client owns and closes its child process.

### Model and reasoning effort

```powershell
.\.venv\bin\python.exe bridge/codex_client.py --list-models
.\.venv\bin\python.exe bridge/codex_client.py --model gpt-5.6-luna --effort low
.\.venv\bin\python.exe bridge/codex_client.py --model gpt-5.6-luna --effort low --message "안녕하세요"
```

Model IDs and supported efforts come from the running Codex server, not a hard-coded list. Omitted options preserve the configured defaults. An unsupported explicit selection is rejected before starting a model turn. The class supports `start_thread(model=...)` and `send_message(thread_id, text, effort=...)` for future Java stdio integration.

Run selection tests with `.\.venv\bin\python.exe -m unittest discover -s tests -v`.


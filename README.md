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
This is development-environment scaffolding; chat, login, and IPC are not implemented yet.

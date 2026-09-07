"""Command-line entry point for the Lumi Codex bridge."""
import argparse
import json
import sys

from codex_client import CodexClient, CodexError
from stdio_bridge import run_stdio


def main():
    # Keep Korean text intact when launched through Windows pipes.
    for stream in (sys.stdin, sys.stdout, sys.stderr):
        if hasattr(stream, 'reconfigure'):
            stream.reconfigure(encoding='utf-8')
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="Check protocol and login without generating a reply")
    parser.add_argument("--message", action="append", help="Send text; repeat for a follow-up in the same thread")
    parser.add_argument("--model", help="Model ID from --list-models; omitted uses Codex configuration")
    parser.add_argument("--effort", help="Reasoning effort supported by the selected model, e.g. low or high")
    parser.add_argument("--list-models", action="store_true", help="List available models and supported reasoning efforts")
    parser.add_argument("--stdio", action="store_true", help="Line-delimited JSON protocol for Java")
    args = parser.parse_args()
    if args.stdio:
        if args.check or args.message or args.list_models:
            parser.error("--stdio cannot be combined with --check, --message or --list-models")
        return run_stdio(model=args.model, effort=args.effort)
    try:
        with CodexClient() as client:
            account = client.get_account()
            if account.get("requiresOpenaiAuth") and not account.get("account"):
                raise CodexError("Login required. Run codex login first.")
            if args.check:
                print(json.dumps({"connected": True, "auth_type": (account.get("account") or {}).get("type")}))
                return 0
            if args.list_models:
                for model in client.list_models():
                    efforts = ", ".join(entry["reasoningEffort"] for entry in model["supportedReasoningEfforts"])
                    print(f"{model['model']} | {model['displayName']} | effort: {efforts} | model default: {model['defaultReasoningEffort']}")
                return 0
            thread_id = client.start_thread(model=args.model)
            if args.message:
                for text in args.message:
                    print(client.send_message(thread_id, text, effort=args.effort), flush=True)
            else:
                print("Codex connected. Enter /quit to exit.")
                while True:
                    text = input("You> ").strip()
                    if text == "/quit":
                        break
                    if text:
                        print("Codex> " + client.send_message(thread_id, text, effort=args.effort), flush=True)
        return 0
    except (EOFError, KeyboardInterrupt):
        return 0
    except (CodexError, OSError, ValueError) as exc:
        print(f"Error: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())


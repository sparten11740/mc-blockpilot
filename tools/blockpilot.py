#!/usr/bin/env python3
"""Sends one Blockpilot script to a running game and prints the reply.

    python3 tools/blockpilot.py --game-dir <run dir> [--json] [--timeout 120] [--wait-ready 180] "status; look"

Reads <game dir>/blockpilot/endpoint.json (port, token file) written by the mod. Exit code: 0 if every command
returned ok, 1 if any returned err, 2 if the game could not be reached. Standard library only.
"""
import argparse
import json
import os
import socket
import sys
import time


def alive(pid):
    if os.name == "nt":
        # Signal 0 means CTRL_C_EVENT on Windows; don't probe there.
        return True
    try:
        os.kill(pid, 0)
        return True
    except ProcessLookupError:
        return False
    except PermissionError:
        return True


def endpoint(game_dir):
    with open(os.path.join(game_dir, "blockpilot", "endpoint.json"), encoding="utf-8") as handle:
        info = json.load(handle)
    # A file left behind by a game that has exited names a port another game may hold by now.
    if not alive(info["pid"]):
        raise ValueError(f"endpoint.json is stale (game process {info['pid']} is not running)")
    with open(info["token_file"], encoding="utf-8") as handle:
        return info["port"], handle.read().strip()


def connect(game_dir, wait_ready, timeout):
    deadline = time.time() + wait_ready
    while True:
        try:
            port, token = endpoint(game_dir)
            sock = socket.create_connection(("127.0.0.1", port), timeout=timeout)
            return sock, token
        except (OSError, ValueError, KeyError) as error:
            if time.time() >= deadline:
                raise SystemExit(f"blockpilot: game not reachable: {error}") from error
            time.sleep(1)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("script", help="commands separated by ;")
    parser.add_argument("--game-dir", default="run", help="the game's run directory (default: run)")
    parser.add_argument("--json", action="store_true", help="print the reply as one JSON object")
    parser.add_argument("--timeout", type=float, default=300, help="seconds to wait for the reply")
    parser.add_argument("--wait-ready", type=float, default=0, help="seconds to wait for the game to come up")
    args = parser.parse_args()

    try:
        sock, token = connect(args.game_dir, args.wait_ready, args.timeout)
    except SystemExit as error:
        print(error, file=sys.stderr)
        return 2
    stream = sock.makefile("rwb")

    def send(line):
        stream.write((line + "\n").encode("utf-8"))
        stream.flush()

    def text_reply():
        lines = []
        while True:
            raw = stream.readline()
            if not raw:
                raise SystemExit("blockpilot: connection closed")
            line = raw.decode("utf-8").rstrip("\n")
            if line == "." or not lines and line.startswith("{"):
                # A script that runs "fmt json" gets that reply as one JSON line already.
                return "\n".join(lines + ([] if line == "." else [line]))
            lines.append(line)

    send("auth " + token)
    greeting = text_reply()
    if not greeting.startswith("ok"):
        print(greeting, file=sys.stderr)
        return 2
    if args.json:
        send("fmt json")
        stream.readline()
    send(args.script.replace("\n", "; "))
    reply = stream.readline().decode("utf-8").rstrip("\n") if args.json else text_reply()
    print(reply)
    if reply.startswith("{"):
        results = json.loads(reply)["results"]
        return 0 if all(result.get("ok") for result in results) else 1
    words = [line.split() for line in reply.splitlines()]
    failed = any(w[:1] == ["err"] or (w[:1] and w[0].startswith("#") and w[1:2] == ["err"]) for w in words)
    return 1 if failed else 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (OSError, SystemExit) as failure:
        if isinstance(failure, SystemExit) and isinstance(failure.code, int):
            raise
        print(f"blockpilot: {failure or 'timed out'}", file=sys.stderr)
        sys.exit(2)

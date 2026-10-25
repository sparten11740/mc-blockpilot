#!/usr/bin/env python3
"""Sends one Blockpilot script to a running game and prints the reply.

    python3 tools/blockpilot.py --game-dir <run dir> [--json] [--timeout 120] [--wait-ready 180 [--wait-world]] \
        "status; look"

Reads <game dir>/blockpilot/endpoint.json (port, token file) written by the mod. Exit code: 0 if every command
returned ok, 1 if any returned err, 2 if the game could not be reached. After a `quit` it waits (within --timeout)
until the game process has exited, and exits 2 if it hasn't. Standard library only.
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


def wait_exit(pid, seconds):
    """True once the process has exited, False if it is still running after the given seconds."""
    if os.name == "nt":
        import ctypes
        kernel32 = ctypes.windll.kernel32
        handle = kernel32.OpenProcess(0x00100000, False, pid)  # SYNCHRONIZE
        if not handle:
            return True
        try:
            return kernel32.WaitForSingleObject(handle, int(seconds * 1000)) == 0
        finally:
            kernel32.CloseHandle(handle)
    deadline = time.time() + seconds
    while alive(pid):
        if time.time() >= deadline:
            return False
        time.sleep(0.1)
    return True


def quit_pid(reply):
    """The game's pid when the reply holds a successful quit (text `ok pid=<n>`, JSON `pid`), else None."""
    if reply.startswith("{"):
        for result in json.loads(reply)["results"]:
            if result.get("ok") and "pid" in result:
                return int(result["pid"])
        return None
    for line in reply.splitlines():
        words = line.split()
        if words and words[0].startswith("#"):
            words = words[1:]
        if len(words) == 2 and words[0] == "ok" and words[1].startswith("pid="):
            return int(words[1][len("pid="):])
    return None


def endpoint(game_dir):
    with open(os.path.join(game_dir, "blockpilot", "endpoint.json"), encoding="utf-8") as handle:
        info = json.load(handle)
    # A file left behind by a game that has exited names a port another game may hold by now.
    if not alive(info["pid"]):
        raise ValueError(f"endpoint.json is stale (game process {info['pid']} is not running)")
    with open(info["token_file"], encoding="utf-8") as handle:
        return info["port"], handle.read().strip()


def connect(game_dir, deadline, timeout):
    while True:
        try:
            port, token = endpoint(game_dir)
            sock = socket.create_connection(("127.0.0.1", port), timeout=timeout)
            return sock, token
        except (OSError, ValueError, KeyError) as error:
            if time.time() >= deadline:
                raise SystemExit(f"blockpilot: game not reachable: {error}") from error
            time.sleep(1)


def summary(results):
    """One stderr line when steps failed or were skipped, else None; results are (kind, code, argument) tuples."""
    errors = [result for result in results if result[0] == "err"]
    skips = sum(1 for result in results if result[0] == "skip")
    if not errors and not skips:
        return None
    line = f"blockpilot: {len(errors)} err, {skips} skip"
    # The parser refuses a script with a syntax error: that step answers "err syntax col <n>: …", every other skip.
    if len(errors) == 1 and errors[0][1] == "syntax" and errors[0][2].startswith("col ") and 1 + skips == len(results):
        return line + " (a syntax error, so nothing ran)"
    return line + " (nothing after the error ran)" if skips else line


def text_results(reply):
    """(kind, code, argument) per result line of a text reply; rows are indented and events start with !."""
    results = []
    for line in reply.splitlines():
        if line.startswith((" ", "!")) or not line:
            continue
        words = line.split(" ", 3)
        if words[0].startswith("#"):
            words = words[1:]
        results.append((words[0], words[1] if len(words) > 1 else "", " ".join(words[2:])))
    return results


def json_results(reply):
    return [("skip", "", "") if result.get("skip") else ("ok", "", "") if result.get("ok")
            else ("err", result.get("err", ""), result.get("arg", "")) for result in json.loads(reply)["results"]]


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("script", help="commands separated by ;")
    parser.add_argument("--game-dir", default="run", help="the game's run directory (default: run)")
    parser.add_argument("--json", action="store_true", help="print the reply as one JSON object")
    parser.add_argument("--timeout", type=float, default=300, help="seconds to wait for the reply")
    parser.add_argument("--wait-ready", type=float, default=0, help="seconds to wait for the game to come up")
    parser.add_argument("--wait-world", action="store_true",
                        help="also wait (within --wait-ready) until the player is in a world")
    args = parser.parse_args()
    deadline = time.time() + args.wait_ready

    try:
        sock, token = connect(args.game_dir, deadline, args.timeout)
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
                raise SystemExit("connection closed")
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
    if args.wait_world:
        while True:
            send("status")
            status = text_reply()
            if not status.startswith("ok menu"):
                break
            if time.time() >= deadline:
                print(f"blockpilot: timed out waiting for a world (last status: {status.splitlines()[0]})",
                      file=sys.stderr)
                return 2
            time.sleep(1)
    if args.json:
        send("fmt json")
        stream.readline()
    send(args.script.replace("\n", "; "))
    reply = stream.readline().decode("utf-8").rstrip("\n") if args.json else text_reply()
    print(reply)
    results = json_results(reply) if reply.startswith("{") else text_results(reply)
    line = summary(results)
    if line:
        print(line, file=sys.stderr)
    pid = quit_pid(reply)
    if pid is not None and not wait_exit(pid, args.timeout):
        print(f"blockpilot: the game (pid {pid}) is still running after {args.timeout:g}s", file=sys.stderr)
        return 2
    return 1 if any(result[0] == "err" for result in results) else 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (OSError, SystemExit) as failure:
        if isinstance(failure, SystemExit) and isinstance(failure.code, int):
            raise
        print(f"blockpilot: {failure or 'timed out'}", file=sys.stderr)
        sys.exit(2)

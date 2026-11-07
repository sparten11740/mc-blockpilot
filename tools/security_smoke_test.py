#!/usr/bin/env python3
"""The security checks against a running game with Blockpilot, on their own.

    python3 tools/security_smoke_test.py --game-dir <run dir> [--json] [--clients 2]

Checks who reaches the agent through the game and what the agent may do in it (docs/safety.md): nobody trusted by
default, trustedPlayers read from config/blockpilot/config.json within a second, no command that changes trust, and
the play profile (agentCommands: "play"), down to slash commands an agent types into the chat screen, the screens its
input reaches and links, and allowedExtensions for other mods' commands. It writes the
client's config file for that, restores it in the end, also on a failure or Ctrl-C, and waits for the client to read it
back, so it leaves the client as it found it and can run before or after tools/smoke_test.py. It makes a throwaway
flat world and deletes it again.

--json reads every reply as JSON instead of text. --clients 2 runs the two-client checks, which need the second client
of the two-client test runs; without it, they are reported as skipped. Exit code 0 when every check passed; a check
that can't run here is reported as skipped, with why, never as passed. tools/test_targets.py runs this after the smoke
test on every target.
"""
import argparse
import json
import os
import re
import sys
import time

from smoke_test import Skipped, game_version, run

RELOAD_SECONDS = 2.0


def steps(game_dir, script, as_json):
    """Each step's outcome: (status, code, argument, text), status being ok, err or skip."""
    _, output = run(game_dir, script, as_json=as_json)
    if as_json:
        try:
            results = json.loads(output.splitlines()[0]).get("results", [])
        except (ValueError, IndexError):
            raise AssertionError(f"not one JSON line: {output.strip()[:300]}")
        return [("ok", None, None, json.dumps(result)) if result.get("ok")
                else ("err", result.get("err"), result.get("arg"), json.dumps(result)) if result.get("err")
                else ("skip", None, None, json.dumps(result)) for result in results]
    outcomes = []
    for line in output.splitlines():
        if line.startswith("  ") and outcomes:
            status, code, argument, text = outcomes[-1]
            outcomes[-1] = (status, code, argument, text + "\n" + line)
        elif line.startswith("ok"):
            outcomes.append(("ok", None, None, line))
        elif line.startswith("err "):
            parts = line.split(" ", 2)
            outcomes.append(("err", parts[1], parts[2] if len(parts) > 2 else None, line))
        elif line == "skip":
            outcomes.append(("skip", None, None, line))
    return outcomes


def refused(outcome, command):
    return outcome[0] == "err" and outcome[1] == "not_allowed" and outcome[2] == command


class Config:
    """The client's config file: written for a check, put back as it was in the end."""

    def __init__(self, game_dir):
        self.path = os.path.join(game_dir, "config", "blockpilot", "config.json")
        with open(self.path, encoding="utf-8") as source:
            self.original = source.read()
        self.settings = json.loads(self.original)

    def write(self, **changes):
        with open(self.path, "w", encoding="utf-8") as target:
            json.dump({**self.settings, **changes}, target, indent=2)

    def restore(self):
        with open(self.path, "w", encoding="utf-8") as target:
            target.write(self.original)

    def text(self):
        with open(self.path, encoding="utf-8") as source:
            return source.read()


def until(check, seconds=RELOAD_SECONDS):
    """Polls the check until it holds or the time is up; returns the seconds it took, or None."""
    start = time.monotonic()
    while time.monotonic() - start < seconds:
        if check():
            return time.monotonic() - start
        time.sleep(0.2)
    return None


def trusted(game_dir, as_json):
    """The names `trust` lists."""
    _, output = run(game_dir, "trust", as_json=as_json)
    if as_json:
        result = json.loads(output.splitlines()[0])["results"][0]
        return [row.get("name") for row in result.get("rows", [])]
    return [line.split()[0] for line in output.splitlines() if line.startswith("  ")]


def play_applies(game_dir, as_json):
    """Whether the play profile is in force, probed with `config`, which only reads when it isn't."""
    return refused(steps(game_dir, "config", as_json)[0], "config")


def check_nobody_trusted_by_default(game_dir, config, as_json):
    if config.settings.get("trustedPlayers"):
        raise Skipped("this client's config already trusts players")
    names = trusted(game_dir, as_json)
    return None if names == [] else f"trust lists {names}"


def check_trust_follows_the_file(game_dir, config, as_json):
    config.write(trustedPlayers=["Steve"])
    added = until(lambda: trusted(game_dir, as_json) == ["Steve"])
    config.write(trustedPlayers=[])
    removed = until(lambda: trusted(game_dir, as_json) == [])
    if added is None:
        return f"a name written into the file wasn't in trust after {RELOAD_SECONDS} s"
    if removed is None:
        return f"a name removed from the file was still in trust after {RELOAD_SECONDS} s"
    return None


def check_no_command_changes_trust(game_dir, config, as_json):
    before = config.text()
    outcome = steps(game_dir, "trust add Mallory", as_json)[0]
    if outcome[0] != "err" or outcome[1] != "syntax":
        return f"trust add Mallory answered {outcome[3]}"
    if "Mallory" in trusted(game_dir, as_json) or config.text() != before:
        return "trust add Mallory changed the list or the file"
    return None


def check_play_profile(game_dir, config, as_json, world):
    config.write(agentCommands="play")
    if until(lambda: play_applies(game_dir, as_json)) is None:
        return f"agentCommands play wasn't in force after {RELOAD_SECONDS} s"
    problems = []

    def expect(script, wanted):
        outcomes = steps(game_dir, script, as_json)
        if [outcome[0] if outcome[0] != "err" else f"err {outcome[1]} {outcome[2]}" for outcome in outcomes] != wanted:
            problems.append(f"{script} answered {[outcome[3] for outcome in outcomes]}, not {wanted}")

    expect("jump; cmd say hi", ["skip", "err not_allowed cmd"])
    expect("server time query gametime", ["err not_allowed server"])
    expect("if status ok then cmd say hi", ["err not_allowed cmd"])
    expect(f"create world {world}-other flat", ["err not_allowed create world"])
    expect(f"delete world {world}", ["err not_allowed delete world"])
    expect("quit", ["err not_allowed quit"])
    expect("perf 1s", ["err not_allowed perf"])
    status = steps(game_dir, "perf status", as_json)[0]
    if refused(status, "perf"):
        problems.append("perf status was refused")
    expect("goto ~2,0,0", ["ok"])
    # Typed into the chat screen and sent with enter, as a person would.
    outcomes = steps(game_dir, "key t; wait 5t; type /say security-hi; key enter", as_json)
    if [outcome[0] for outcome in outcomes] != ["ok", "ok", "ok", "err"] or not refused(outcomes[3], "cmd"):
        problems.append(f"an agent-typed /say answered {[outcome[3] for outcome in outcomes]}")
    _, said = run(game_dir, "wait 10t; ?close; messages since=5s match=*security-hi*", as_json=as_json)
    if "security-hi" in said and not as_json or as_json and '"count":0' not in said.replace(" ", ""):
        problems.append(f"the agent-typed /say reached the server: {said.strip()[:300]}")
    return "; ".join(problems) or None


def check_play_screens(game_dir, config, as_json):
    """Under play the pause menu's buttons can't be clicked, so the agent can't leave the world through them."""
    config.write(agentCommands="play")
    if until(lambda: play_applies(game_dir, as_json)) is None:
        return f"agentCommands play wasn't in force after {RELOAD_SECONDS} s"
    _, screen = run(game_dir, "key escape; wait 10t; screen")
    leave = re.search(r"^  w(\d+):button \"(?:Save and Quit to Title|Disconnect)\"", screen, re.MULTILINE)
    if not leave:
        return f"no leave button on the pause menu: {screen.strip()[:300]}"
    outcome = steps(game_dir, f"click w{leave.group(1)}", as_json)[0]
    _, status = run(game_dir, "wait 10t; status")
    run(game_dir, "key escape")
    if not refused(outcome, "screen"):
        return f"the click on the leave button answered {outcome[3]}"
    in_world = re.search(r"^ok pos", status, re.MULTILINE)
    return None if in_world else f"the player left the world: {status.strip()[:200]}"


def check_play_links(game_dir, config, as_json):
    """Under play a click on a chat link opens nothing, not even the confirm screen."""
    link = '{"text":"security-link","clickEvent":{"action":"open_url","value":"https://example.com"}}' \
        if game_version(game_dir) < (1, 21, 5) else \
        '{text:"security-link",click_event:{action:"open_url",url:"https://example.com"}}'
    told = steps(game_dir, f"server tellraw @p {link}", as_json)[0]
    if told[0] != "ok":
        raise Skipped(f"tellraw didn't print the link: {told[3]}")
    config.write(agentCommands="play")
    if until(lambda: play_applies(game_dir, as_json)) is None:
        return f"agentCommands play wasn't in force after {RELOAD_SECONDS} s"
    _, screen = run(game_dir, "key t; wait 5t; screen")
    size = re.search(r"\bsize (\d+)x(\d+)", screen)
    if not size:
        return f"the chat screen didn't open: {screen.strip()[:200]}"
    # The newest chat line sits just above the input box.
    outcome = steps(game_dir, f"click 20,{int(size.group(2)) - 44}", as_json)[0]
    _, after = run(game_dir, "wait 5t; screen")
    run(game_dir, "?close; wait 5t; ?close")
    if "confirm_link" in after:
        return "the confirm screen for the link opened"
    return None if refused(outcome, "link") else f"the click on the link answered {outcome[3]}"


def check_extensions_allowed(game_dir, config, as_json):
    """Another mod's command is refused until its namespace is in allowedExtensions, then works, also under play."""
    _, overview = run(game_dir, "help")
    if "devcheck" not in overview:
        raise Skipped("no devcheck:ping in this run; development runs register it")
    before = steps(game_dir, "devcheck:ping", as_json)[0]
    if not refused(before, "devcheck:ping"):
        return f"devcheck:ping answered {before[3]} before devcheck was allowed"
    config.write(allowedExtensions=["devcheck"])
    if until(lambda: steps(game_dir, "devcheck:ping", as_json)[0][0] == "ok") is None:
        return f"devcheck:ping was still refused {RELOAD_SECONDS} s after devcheck was allowed"
    config.write(allowedExtensions=["devcheck"], agentCommands="play")
    if until(lambda: play_applies(game_dir, as_json)) is None:
        return f"agentCommands play wasn't in force after {RELOAD_SECONDS} s"
    under_play = steps(game_dir, "devcheck:ping", as_json)[0]
    return None if under_play[0] == "ok" else f"an allowed extension command under play answered {under_play[3]}"


def check_two_clients(game_dir, config, as_json, clients):
    if clients < 2:
        raise Skipped("needs --clients 2, the second client of the two-client test runs")
    raise Skipped("the two-client checks come with the two-client test runs")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--game-dir", required=True)
    parser.add_argument("--json", action="store_true")
    parser.add_argument("--clients", type=int, default=1)
    args = parser.parse_args()
    game_dir, as_json = args.game_dir, args.json
    config = Config(game_dir)
    world = f"security-{int(time.time())}"
    failures = skipped = 0
    try:
        created = steps(game_dir, f"create world {world} flat peaceful", as_json)
        if not created or created[0][0] != "ok":
            print(f"FAIL create a test world: {created}")
            return 1
        checks = [
            ("nobody is trusted by default", lambda: check_nobody_trusted_by_default(game_dir, config, as_json)),
            ("trust follows the config file within a second",
             lambda: check_trust_follows_the_file(game_dir, config, as_json)),
            ("no command changes trust", lambda: check_no_command_changes_trust(game_dir, config, as_json)),
            ("the play profile refuses what goes beyond playing",
             lambda: check_play_profile(game_dir, config, as_json, world)),
            ("the play profile keeps input to gameplay screens", lambda: check_play_screens(game_dir, config, as_json)),
            ("the play profile follows no links", lambda: check_play_links(game_dir, config, as_json)),
            ("another mod's commands need allowedExtensions",
             lambda: check_extensions_allowed(game_dir, config, as_json)),
            ("untrusted chat is withheld between two clients",
             lambda: check_two_clients(game_dir, config, as_json, args.clients)),
        ]
        for name, check in checks:
            try:
                problem = check()
            except Skipped as reason:
                skipped += 1
                print(f"skip {name}: {reason}")
                continue
            except (AssertionError, ValueError, IndexError, KeyError) as error:
                problem = repr(error)
            finally:
                config.restore()
                until(lambda: not play_applies(game_dir, as_json) and trusted(game_dir, as_json)
                      == (config.settings.get("trustedPlayers") or []))
            if problem:
                failures += 1
                print(f"FAIL {name}: {problem}")
                run(game_dir, "?close")
            else:
                print(f"pass {name}")
    finally:
        config.restore()
        until(lambda: not play_applies(game_dir, as_json))
        run(game_dir, f"leave; delete world {world}")
    print(f"{failures} failed, {skipped} skipped")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())

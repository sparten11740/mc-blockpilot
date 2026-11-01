#!/usr/bin/env python3
"""End-to-end smoke test against a running game with Blockpilot.

    python3 tools/smoke_test.py --game-dir <run dir> [--json] [--shapes <file>]

Creates a throwaway flat test world, exercises the command groups (perception, actions, inventory, containers,
crafting, raw input, observation, the integrated server, sampling, performance capture, snapshots, JSON output),
checks each reply, then leaves and deletes the world. Exit code 0 when every step passed.

--json runs the same steps in JSON mode and checks the structure of each reply instead of its text: one JSON line
with one result per step, each ok unless the step expects an error.

--shapes writes the JSON structure (field names, row field names) of a set of queries to a file, so runs on two
loaders can be compared with diff.

Every step goes through the launcher the game writes into <run dir>/blockpilot at start.
"""
import argparse
import json
import os
import re
import subprocess
import sys
import time

# Read-only commands that must not move, turn or reselect anything, nor make the client send packets.
PERCEPTION = ["status", "look 8", "find grass_block 4 2", "block ~0,-1,0", "block ~0,-1,0 side=server",
              "blocks ~-1,-1,-1 ~1,0,1", "screen", "worlds", "servers", "sounds limit=1", "messages limit=1",
              "particles", "log mark", "help look"]
# What a client sends on its own while standing still: position reminders, keep-alives, chunk batch acks, and from
# 26.1 on the end of every client tick.
BACKGROUND_PACKETS = {"move_player_pos", "move_player_status_only", "keep_alive", "chunk_batch_received",
                      "client_tick_end"}
SHAPE_SCRIPTS = ["status", "inv", "look 4", "find grass_block 4 2", "block ~0,-1,0 side=server",
                 "blocks ~0,-1,0 ~1,-1,1", "worlds", "snapshot list", "tick query", "log mark",
                 "server time query day", "sample status every 1t x2", "wait status until ok", "help status",
                 "messages limit=1", "particles", "nbt e999999"]


def launcher(game_dir):
    if os.name == "nt":
        return ["cmd", "/c", os.path.join(game_dir, "blockpilot", "blockpilot.cmd")]
    return [os.path.join(game_dir, "blockpilot", "blockpilot")]


def run(game_dir, script, as_json=False, timeout=180):
    command = launcher(game_dir) + ["--game-dir", game_dir, "--timeout", str(timeout)]
    if as_json:
        command.append("--json")
    command.append(script)
    done = subprocess.run(command, capture_output=True, text=True, encoding="utf-8", timeout=timeout + 30)
    return done.returncode, done.stdout + done.stderr


def shape(result):
    """Field names of one JSON result, with the union of its rows' field names."""
    fields = sorted(key for key in result if key != "rows")
    rows = sorted({key for row in result.get("rows", []) for key in row})
    return " ".join(fields) + (" rows[" + " ".join(rows) + "]" if rows else "")


def json_problem(script, output, expected_error):
    """Why a JSON reply is wrong, or None."""
    try:
        reply = json.loads(output.splitlines()[0])
    except (ValueError, IndexError):
        return "not one JSON line"
    results = reply.get("results", [])
    steps = len([step for step in re.split(r"(?<!\\);", script) if step.strip()])
    if len(results) != steps:
        return f"{len(results)} results for {steps} steps"
    if not all(isinstance(result.get("ok"), bool) for result in results):
        return "a result without ok"
    if not expected_error and not all(result["ok"] for result in results):
        return "a step failed"
    return None


def check_perception_is_passive(game_dir):
    """Runs every perception command across a few dozen ticks; returns a problem, or None if nothing changed."""
    script = "inv; status; perf start passive; " + "; ".join(f"{query}; wait 2t" for query in PERCEPTION) \
        + "; perf stop; status; inv"
    code, output = run(game_dir, script)
    if code != 0:
        return f"exit {code}: {output.strip()}"
    statuses = [re.sub(r" daytime\d+", "", line) for line in output.splitlines() if line.startswith("ok pos ")]
    if len(statuses) < 2 or len(set(statuses)) != 1:
        return f"status changed: {statuses}"
    if "\nok same\n" not in output + "\n":
        return "the inventory or selected slot changed"
    sent = set(re.findall(r"^  out (\S+) ", output, re.MULTILINE)) - BACKGROUND_PACKETS
    return f"perception sent packets: {sorted(sent)}" if sent else None


def write_shapes(game_dir, path):
    """Writes one line per query; returns how many replies could not be read."""
    lines = []
    unreadable = 0
    for script in SHAPE_SCRIPTS:
        _, output = run(game_dir, script, as_json=True)
        try:
            reply = json.loads(output.splitlines()[0])
            lines.append(f"{script} => " + " | ".join(shape(result) for result in reply["results"]))
        except (ValueError, IndexError, KeyError):
            unreadable += 1
            lines.append(f"{script} => unreadable: {output.strip()}")
    with open(path, "w", encoding="utf-8") as handle:
        handle.write("\n".join(lines) + "\n")
    print(f"{'FAIL' if unreadable else 'pass'} json shapes: {len(lines)} written to {path}, {unreadable} unreadable")
    return unreadable


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--game-dir", default="run")
    parser.add_argument("--json", action="store_true", help="run the steps in JSON mode and check reply structure")
    parser.add_argument("--shapes", help="also write the JSON shapes of a set of queries to this file")
    args = parser.parse_args()
    world = f"smoke-{int(time.time())}"
    give = "; ".join(f"server give @p minecraft:{item}" for item in
                     ["oak_log 4", "iron_sword", "shield", "iron_helmet", "cobblestone 16"])
    steps = [
        ("create a test world", f"create world {world} flat peaceful rule:doDaylightCycle=false",
         [r"ok joined " + world]),
        ("status", "wait 1s; status", [r"ok pos -?\d", r"overworld"]),
        ("help", "help; help goto; help errors", [r"movement:", r"goto <target>", r"no_item"]),
        ("give items", give + "; inv", [r"oak_log x4", r"iron_sword", r"shield"]),
        ("craft in the 2x2 grid", "craft oak_planks x8; craft crafting_table", [r"craft 8 oak_planks",
                                                                                r"craft 1 crafting_table"]),
        ("place and read a block", "place crafting_table ~2,0,0; block ~2,0,0", [r"ok crafting_table"]),
        # Regression: place aimed at the top of the carpet's cell, in the air above it: "cannot see the face".
        ("place on a thin block", "server as=player setblock ~0 ~ ~2 minecraft:white_carpet; "
                                  "place cobblestone ~0,1,2; block ~0,1,2; "
                                  "server as=player fill ~0 ~ ~2 ~0 ~1 ~2 minecraft:air", [r"ok cobblestone"]),
        # Regression: the crosshair met the stone's near face, and the click put the block there, on the wrong side.
        ("place against a face out of sight", "server as=player setblock ~2 ~2 ~ minecraft:stone; "
                                              "?place cobblestone ~3,2,0; find cobblestone 6; "
                                              "server as=player setblock ~2 ~2 ~ minecraft:air",
         [r"err out_of_reach", r"^ok none"], True),
        ("craft at a table", "craft furnace", [r"craft 1 furnace"]),
        ("mine", "select iron_sword; mine ~2,0,0; block ~2,0,0", [r"mine 1/1 crafting_table", r"ok air"]),
        ("mine names what is in the way", "server as=player setblock ~ ~ ~2 minecraft:stone; "
                                          "server as=player setblock ~ ~ ~3 minecraft:dirt; ?mine ~0,0,3; "
                                          "server as=player fill ~ ~ ~2 ~ ~ ~3 minecraft:air",
         [r"err out_of_reach ~0,0,3 is behind stone ~0,0,2"], True),
        # Regression: mine aimed at the middle of the carpet's cell, passed over it and hit the ground behind.
        ("mine a thin block", "server as=player setblock ~ ~ ~2 minecraft:white_carpet; mine ~0,0,2; block ~0,0,2",
         [r"mine 1/1 white_carpet", r"ok air"]),
        ("find by state", "server as=player setblock ~1 ~ ~-2 minecraft:oak_log[axis=y]; "
                          "server as=player setblock ~2 ~ ~-2 minecraft:oak_log[axis=x]; find oak_log[axis=x] 4 1; "
                          "server as=player fill ~1 ~ ~-2 ~2 ~ ~-2 minecraft:air", [r"^  oak_log ~2,0,-2$"]),
        ("block state errors", "?find oak_log[axis=up]; ?find stone[axis=x]",
         [r"err syntax axis of oak_log takes x\|y\|z, not up", r"err syntax stone has no states"], True),
        ("inventory moves", "offhand shield; equip iron_helmet; inv", [r"head:iron_helmet", r"offhand:shield"]),
        ("container transfer", "server as=player setblock ~3 ~ ~ minecraft:chest; open ~3,0,0; put cobblestone 5; "
                               "take cobblestone 2; close", [r"moved 5", r"moved 2"]),
        ("server-side nbt", "nbt ~3,0,0 path=Items[0].count", [r"ok 3"]),
        ("side=server read", "block ~3,0,0 side=server; find chest 8 side=server", [r"ok chest", r"chest ~3,0,0"]),
        ("attack", "server as=player summon minecraft:cow ~3 ~ ~2; wait 5t; attack cow", [r"killed 1"]),
        ("server runs at the player", "server as=player tp @s ~40 ~ ~; server setblock ~ ~-1 ~ minecraft:gold_block; "
                                      "block ~0,-1,0; server setblock ~ ~-1 ~ minecraft:grass_block; "
                                      "server as=player tp @s ~-40 ~ ~", [r"ok gold_block"]),
        ("goto", "goto ~6,0,4; status", [r"ok \d+(\.\d+)?s"]),
        ("move", "turn -90 0; move forward 2; move back 5t", [r"ok moved 2\.\d", r"ok moved \d"]),
        ("move into a wall", "server as=player fill ~2 ~ ~-1 ~2 ~1 ~1 stone; wait 3t; ?move forward 8; "
                             "server as=player fill ~-2 ~ ~-2 ~3 ~2 ~2 air", [r"err stuck moved="], True),
        ("sample while moving", "sample status every 2t while move back 2", [r"ok moved \d.* samples=\d",
                                                                            r"^  t\d+ c\d+ pos "]),
        ("look and find", "look 8; find grass_block 4 2", [r"^ok", r"grass_block"]),
        ("region dump", "blocks ~-1,-1,-1 ~1,0,1", [r"a=grass_block", r"y-61:"]),
        ("raw input", "key press key.inventory; wait 3t; screen; close; mouse move 40 0 4t",
         [r"creative_inventory|inventory"]),
        ("fly", "fly on; wait 5t; fly off", [r"ok flying", r"ok not flying"]),
        ("screenshot", "screenshot 256", [r"^ok .+\.png t\d+$"]),
        ("screenshot burst", "screenshot burst=2 every=5t 256", [r"frames=2"]),
        ("camera", "camera at ~4,3,4 look ~0,1,0; status; camera reset", [r"ok camera ", r"cam:"]),
        ("logs", "server title @p actionbar {\"text\":\"smoke\"}; wait 3t; messages kind=actionbar; sounds limit=1; "
                 "particles; log mark", [r"actionbar t\d+ smoke", r"mark=\d+"]),
        ("tick control", "tick freeze; tick step 10; tick unfreeze", [r"frozen=true", r"frozen=false"]),
        ("wait until", "wait block ~0,-1,0 until id==grass_block timeout=2s", [r"ok \d"]),
        ("sample", "sample status every 2t x3", [r"samples=3"]),
        ("perf", "perf start smoke; wait 1s; perf stop", [r"smoke \d", r"mspt="]),
        ("snapshot", "snapshot save s1; snapshot list; snapshot restore s1", [r"saved s1", r"restored s1"]),
    ]
    failures = 0
    for name, script, expectations, *expected_error in steps:
        code, output = run(args.game_dir, script, as_json=args.json)
        if args.json:
            problem = json_problem(script, output, expected_error)
        else:
            missing = [pattern for pattern in expectations if not re.search(pattern, output, re.MULTILINE)]
            # A step that checks an error reply exits non-zero, as every reply with an err line does.
            problem = f"exit {code}, missing {missing}" if (code != 0 and not expected_error) or missing else None
        if problem:
            failures += 1
            print(f"FAIL {name}: {problem}\n    " + output.strip().replace("\n", "\n    "))
        else:
            print(f"pass {name}")
    problem = check_perception_is_passive(args.game_dir)
    if problem:
        failures += 1
        print(f"FAIL perception is passive: {problem}")
    else:
        print("pass perception is passive")
    code, output = run(args.game_dir, "status; inv", as_json=True)
    try:
        reply = json.loads(output.splitlines()[0])
        assert reply["results"][0]["ok"] and "pos" in reply["results"][0], reply
        print("pass json output")
    except (ValueError, AssertionError, IndexError, KeyError) as error:
        failures += 1
        print(f"FAIL json output: {error}\n    {output.strip()}")
    if args.shapes:
        failures += 1 if write_shapes(args.game_dir, args.shapes) else 0
    code, output = run(args.game_dir, f"leave; delete world {world}")
    if code == 0 and f"deleted {world}" in output:
        print("pass clean up")
    else:
        failures += 1
        print(f"FAIL clean up: {output.strip()}")
    total = len(steps) + 3 + (1 if args.shapes else 0)
    print(f"{total - failures}/{total} passed")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())

#!/usr/bin/env python3
"""End-to-end smoke test against a running game with Blockpilot.

    python3 tools/smoke_test.py --game-dir <run dir>

Creates a throwaway flat test world, exercises the command groups (perception, actions, inventory, containers,
crafting, raw input, observation, the integrated server, sampling, performance capture, snapshots, JSON output),
checks each reply, then leaves and deletes the world. Exit code 0 when every step passed.
"""
import argparse
import json
import os
import re
import subprocess
import sys
import time

CLIENT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "blockpilot.py")


def run(game_dir, script, as_json=False, timeout=180):
    command = [sys.executable, "-I", CLIENT, "--game-dir", game_dir, "--timeout", str(timeout)]
    if as_json:
        command.append("--json")
    command.append(script)
    done = subprocess.run(command, capture_output=True, text=True, timeout=timeout + 30)
    return done.returncode, done.stdout + done.stderr


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--game-dir", default="run")
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
        ("craft at a table", "craft furnace", [r"craft 1 furnace"]),
        ("mine", "select iron_sword; mine ~2,0,0; block ~2,0,0", [r"mine 1/1 crafting_table", r"ok air"]),
        ("inventory moves", "offhand shield; equip iron_helmet; inv", [r"head:iron_helmet", r"offhand:shield"]),
        ("container transfer", "server as=player setblock ~3 ~ ~ minecraft:chest; open ~3,0,0; put cobblestone 5; "
                               "take cobblestone 2; close", [r"moved 5", r"moved 2"]),
        ("server-side nbt", "nbt ~3,0,0 path=Items[0].count", [r"ok 3"]),
        ("side=server read", "block ~3,0,0 side=server; find chest 8 side=server", [r"ok chest", r"chest ~3,0,0"]),
        ("attack", "server as=player summon minecraft:cow ~3 ~ ~2; wait 5t; attack cow", [r"killed 1"]),
        ("goto", "goto ~6,0,4; status", [r"ok \d+(\.\d+)?s"]),
        ("look and find", "look 8; find grass_block 4 2", [r"^ok", r"grass_block"]),
        ("region dump", "blocks ~-1,-1,-1 ~1,0,1", [r"a=grass_block", r"y-61:"]),
        ("raw input", "key press key.inventory; wait 3t; screen; close; mouse move 40 0 4t",
         [r"creative_inventory|inventory"]),
        ("fly", "fly on; wait 5t; fly off", [r"ok flying", r"ok not flying"]),
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
    for name, script, expectations in steps:
        code, output = run(args.game_dir, script)
        missing = [pattern for pattern in expectations if not re.search(pattern, output, re.MULTILINE)]
        if code != 0 or missing:
            failures += 1
            print(f"FAIL {name}: exit {code}, missing {missing}\n    " + output.strip().replace("\n", "\n    "))
        else:
            print(f"pass {name}")
    code, output = run(args.game_dir, "status; inv", as_json=True)
    try:
        reply = json.loads(output.splitlines()[0])
        assert reply["results"][0]["ok"] and "pos" in reply["results"][0], reply
        print("pass json output")
    except (ValueError, AssertionError, IndexError, KeyError) as error:
        failures += 1
        print(f"FAIL json output: {error}\n    {output.strip()}")
    code, output = run(args.game_dir, f"leave; delete world {world}")
    if code == 0 and f"deleted {world}" in output:
        print("pass clean up")
    else:
        failures += 1
        print(f"FAIL clean up: {output.strip()}")
    print(f"{len(steps) + 2 - failures}/{len(steps) + 2} passed")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())

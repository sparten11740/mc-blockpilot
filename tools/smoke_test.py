#!/usr/bin/env python3
"""End-to-end smoke test against a running game with Blockpilot.

    python3 tools/smoke_test.py --game-dir <run dir> [--json] [--baritone] [--shapes <file>]

Creates a throwaway flat test world, exercises the command groups (perception, actions, inventory, containers,
crafting, raw input, observation, the integrated server, sampling, performance capture, snapshots, JSON output),
checks each reply and what input and capture do in the game, then leaves and deletes the world. Exit code 0 when every
step passed; a step that can't run here, such as one needing ffmpeg, is reported as skipped, with why.

--json runs the same steps in JSON mode and checks the structure of each reply instead of its text: one JSON line
with one result per step, each ok unless the step expects an error.

--baritone, for a game with Baritone installed, runs only the check that goto and mine walk through it, as the other
steps expect the built-in pathfinder. Without it, that check is skipped.

--shapes writes the JSON structure (field names, row field names) of a set of queries to a file, so runs on two
loaders can be compared with diff.

Every step goes through the launcher the game writes into <run dir>/blockpilot at start.
tools/test_targets.py runs this on every target.
"""
import argparse
import glob
import gzip
import json
import math
import os
import re
import shutil
import struct
import subprocess
import sys
import threading
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
SHAPE_OF_AN_ERROR = "nbt e999999"
# The game's select-all shortcut: Command on macOS, Control elsewhere.
SELECT_ALL = "left_win+a" if sys.platform == "darwin" else "left_control+a"
# Facing south from a block's centre at a known place: a block at ~0,0,2 is under the crosshair at pitch 40.
AIMED = "server as=player tp @s 0.5 -60 0.5 0 40; wait 3t"
LEVEL = "server as=player tp @s 0.5 -60 0.5 0 0; wait 5t"
# Steps a game may not be able to run: recording needs ffmpeg, and sound the game's audio capture.
MAY_BE_UNSUPPORTED = {"record", "record with sound"}
# Leaves that place empty; the stone is there so that the fill always changes something, which it must not to fail.
# The stone may be left over from a check that was interrupted, so placing it again mustn't fail the step.
CLEAR = "?server setblock 6 -57 6 minecraft:stone; server fill -3 -60 -3 6 -57 6 minecraft:air"
# A chest beside the player, open, with 16 saplings in its first slot.
CHEST = f"{CLEAR}; {AIMED}; server as=player setblock ~3 ~ ~ minecraft:chest; " \
        "server item replace block ~3 ~ ~ container.0 with minecraft:oak_sapling 16; open ~3,0,0"


class Skipped(Exception):
    """A check that can't run here, with why."""


def unsupported_reason(output, as_json):
    """Why the game can't do what a reply was about, if that's what it answered, or None."""
    if not as_json:
        match = re.search(r"^err unsupported (.+)$", output, re.MULTILINE)
        return match.group(1) if match else None
    try:
        results = json.loads(output.splitlines()[0]).get("results", [])
    except (ValueError, IndexError):
        return None
    return next((result.get("arg") for result in results if result.get("err") == "unsupported"), None)


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


def check_grammar_file(game_dir):
    """Every row `help grammar` prints is a line of docs/grammar.ebnf; returns a problem, or None."""
    code, output = run(game_dir, "help grammar", as_json=True)
    if code != 0:
        return f"exit {code}: {output.strip()}"
    path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "docs", "grammar.ebnf")
    with open(path, encoding="utf-8") as source:
        lines = set(source.read().splitlines())
    # The file is Blockpilot's own grammar, its sable: commands included; another mod's commands, such as the test
    # runs' devcheck:ping, have rules named <mod>.<command> for a mod the file doesn't know.
    def namespace(rule):
        named = re.match(r"([a-z0-9_-]+)\.", rule)
        return named.group(1) if named else None
    checked = {namespace(line) for line in lines}
    missing = [row["ebnf"] for row in json.loads(output.splitlines()[0])["results"][0]["rows"]
               if row["ebnf"] not in lines and namespace(row["ebnf"]) in checked]
    return f"not in docs/grammar.ebnf (rerun tools/grammar.py): {missing[:3]}" if missing else None


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


def game_version(game_dir):
    """The running game's version, as a tuple of numbers to compare."""
    with open(os.path.join(game_dir, "blockpilot", "endpoint.json"), encoding="utf-8") as source:
        return tuple(int(part) for part in json.load(source)["minecraft"].split("."))


# Names in the game's data that changed between versions, set from the running game's version as the test starts:
# the short grass block (1.20.3 renamed it from grass) and an item stack's count (1.20.5 made it lowercase).
GRASS = "short_grass"
COUNT = "count"


def use_names_of(version):
    global GRASS, COUNT
    GRASS = "short_grass" if version >= (1, 20, 3) else "grass"
    COUNT = "count" if version >= (1, 20, 5) else "Count"


def version_steps(version):
    """The steps whose commands or answers depend on the game's version."""
    if version >= (1, 21, 11):
        graphics = "option graphicsPreset fast; option reset", [r"graphicsPreset fast was", r"restored graphicsPreset"]
    else:
        graphics = "option graphicsMode fast; option reset", [r"graphicsMode fast was", r"restored graphicsMode"]
    # Key names are the running game's, and 26.3's key table has no F25.
    if version >= (26, 3):
        function_key = "?key press f25", [r"^err syntax unknown key f25; closest: "], True
    else:
        function_key = "key press f25", [r"^ok"]
    # The game polls for this key as it handles the drop key: Command on macOS before 1.21.11, else Control.
    drop_all = "left_win" if sys.platform == "darwin" and version < (1, 21, 11) else "left_control"
    polled_key = (f"server item replace entity @p hotbar.6 with minecraft:cobblestone 16; select 6; wait 2t; "
                  f"key press key.drop; wait 5t; status; key down {drop_all}; wait 2t; key press key.drop; wait 5t; "
                  f"key up {drop_all}; status", [r"hand:cobblestone x15", r"!hand:cobblestone x15[\s\S]*\bhand:"])
    return [("graphics option sets and resets", *graphics), ("key names are the game's", *function_key),
            ("a held key is seen when the game polls for it", *polled_key)]


def means(output, name):
    """The mean red, green and blue a `pixels` reply gives for a named part of the frame."""
    match = re.search(rf"\b{name} rgb=([\d.]+),([\d.]+),([\d.]+)", output)
    if not match:
        raise AssertionError(f"no {name} in: {output.strip()}")
    return tuple(float(channel) for channel in match.groups())


def upright(sky, ground):
    """Whether sky is above ground in a level view over grass: blue over red at the top, red over blue at the bottom."""
    return sky[2] > sky[0] and ground[2] < ground[0]


def advancements_button(game_dir):
    """Opens the pause menu and returns its button for the advancements, a screen of their own on every version."""
    _, output = run(game_dir, "key escape; wait 5t; screen full")
    widget = re.search(r"\bw(\d+):button \"Advancements\"", output)
    if not widget:
        raise AssertionError(f"no Advancements button: {output.strip()}")
    return f"w{widget.group(1)}"


def check_buttons_in_screen(game_dir):
    """Left click on a pause menu button, through the cursor as a player's mouse does."""
    _, output = run(game_dir, f"cursor {advancements_button(game_dir)}; wait 2t; mouse click left; wait 5t; screen; "
                              "close")
    changed = re.search(r"^ok advancements", output, re.MULTILINE)
    return None if changed else f"the screen didn't change: {output.strip()}"


def check_cursor_stays_across_screens(game_dir):
    """A click that opens another screen leaves the cursor where the agent put it, which 26.3 would forget."""
    _, output = run(game_dir, f"cursor {advancements_button(game_dir)}; wait 2t; screen; mouse click left; wait 5t; "
                              "screen; close")
    cursors = re.findall(r"\bcursor (\d+,\d+)", output)
    if not re.search(r"^ok advancements", output, re.MULTILINE):
        return f"the click opened no screen: {output.strip()}"
    return None if len(cursors) == 2 and cursors[0] == cursors[1] else f"the cursor went {cursors}: {output.strip()}"


def check_mouse_on_slots(game_dir):
    """Right and shift clicks at a slot's place on screen, through the mouse, unlike `click s<i>`."""
    _, output = run(game_dir, f"{CHEST}; screen full")
    size = re.search(r"\bsize (\d+)x(\d+)", output)
    if not size:
        return f"no chest screen: {output.strip()}"
    # A 9x3 chest's window is 176x168 and centred; the middle of its first slot is 16,26 from the corner.
    x = (int(size.group(1)) - 176) // 2 + 16
    y = (int(size.group(2)) - 168) // 2 + 26
    _, output = run(game_dir, f"click {x},{y} right; wait 3t; click s1; wait 3t; nbt ~3,0,0 path=Items[1].{COUNT}")
    # Before 1.20.5 the count is a byte, which nbt writes as 8b.
    if not re.search(r"^ok 8b?$", output, re.MULTILINE):
        return f"a right click didn't take half the stack: {output.strip()}"
    # Without the modifier in the event, the click only picks the stack up, so it never reaches the player's slots.
    _, output = run(game_dir, f"key down left_shift; wait 2t; click {x + 18},{y}; wait 3t; key up left_shift; wait 2t; "
                              "screen full; close")
    moved = re.search(r"\bs[2-6]\d:oak_sapling x8", output)
    return None if moved else f"a shift click didn't move the stack: {output.strip()}"


def check_camera_turns(game_dir):
    """`mouse move` to the right turns the camera to the right, by about as much as the mouse's sensitivity gives."""
    code, output = run(game_dir, f"{LEVEL}; status; mouse move 40 0 4t; wait 3t; status", as_json=True)
    if code != 0:
        return f"exit {code}: {output.strip()}"
    yaws = [result["yaw"] for result in json.loads(output.splitlines()[0])["results"] if "yaw" in result]
    return None if len(yaws) == 2 and 0 < yaws[1] - yaws[0] < 20 else f"yaw went {yaws}"


def check_cursor_moves(game_dir):
    """`mouse move` moves the cursor in a screen by the GUI units asked for; `screen` cuts off any fraction."""
    _, output = run(game_dir, "key escape; wait 5t; cursor 10,10; wait 2t; mouse move 30 20 3t; wait 2t; screen; close")
    return None if re.search(r"\bcursor (39|40),(29|30)\b", output) else f"not at 40,30: {output.strip()}"


def check_server_after_pause(game_dir):
    """Behind the game menu a server step fails at once with err paused; right after close, in the same script, one
    waits for the server to take the unpause, which it does at its next tick, and runs."""
    _, output = run(game_dir, "key escape; wait 5t; ?server time query gametime; close; server time query gametime")
    results = [line for line in output.splitlines() if re.match(r"(ok|err)\b", line)]
    if len(results) != 5 or not results[2].startswith("err paused") or not results[4].startswith("ok "):
        return f"not err paused behind the menu and ok after close: {output.strip()[:500]}"
    return None


def check_frame_upright(game_dir):
    """The frame `pixels` reads has the sky at the top, in the sky's colour."""
    _, output = run(game_dir, f"{CLEAR}; {LEVEL}; option hud off; wait 3t; pixels top=0,0,1,0.2 bottom=0,0.8,1,1; "
                              "option reset")
    sky, ground = means(output, "top"), means(output, "bottom")
    return None if upright(sky, ground) else f"sky {sky}, ground {ground}"


def check_world_position_in_frame(game_dir):
    """`pixels @<pos>` finds a block in the frame, which takes the camera the game rendered with."""
    _, output = run(game_dir, f"{CLEAR}; {LEVEL}; server as=player setblock ~ ~1 ~3 minecraft:red_concrete; "
                              "server as=player setblock ~1 ~1 ~3 minecraft:blue_concrete; wait 5t; "
                              "pixels red=@~0,1,3 blue=@~1,1,3 r=0.3")
    red, blue = means(output, "red"), means(output, "blue")
    return None if red[0] > red[2] and blue[2] > blue[0] else f"red block {red}, blue block {blue}"


def check_hud_hides(game_dir):
    """`option hud off` takes the hotbar off the frame."""
    _, output = run(game_dir, f"{CLEAR}; {LEVEL}; option hud off; wait 3t; pixels hidden=0.35,0.9,0.65,1; "
                              "option hud on; wait 3t; pixels shown=0.35,0.9,0.65,1; option reset")
    hidden, shown = means(output, "hidden"), means(output, "shown")
    difference = max(abs(without - with_hud) for without, with_hud in zip(hidden, shown))
    return None if difference > 3 else f"without {hidden}, with {shown}"


def check_live_particles(game_dir):
    """A capture counts the particles alive when it stops."""
    script = f"{CLEAR}; {LEVEL}; perf start live; wait 5t; " \
             "server as=player particle minecraft:flame ~ ~1 ~2 0.5 0.5 0.5 0 200; wait 5t; perf stop"
    _, output = run(game_dir, script, as_json=True)
    live = json.loads(output.splitlines()[0])["results"][-1]["summary"]["client"]["particles"]["live"]
    return None if live.get("flame", 0) > 0 else f"live particles: {live}"


def check_render_stages(game_dir):
    """A capture times the render stages on NeoForge and Forge, the loaders with events for them."""
    with open(os.path.join(game_dir, "blockpilot", "endpoint.json"), encoding="utf-8") as source:
        loader = json.load(source)["loader"]
    _, output = run(game_dir, "perf start stages; wait 5t; perf stop", as_json=True)
    stages = json.loads(output.splitlines()[0])["results"][-1]["summary"]["client"]["render_stages"]
    return None if bool(stages) == (loader in ("neoforge", "forge")) else f"on {loader}: {stages}"


def check_stop_leaves_timed_running(game_dir, world):
    """Stop ends the wait of a timed perf or record, and what it timed goes on until stopped for itself."""
    for start, status, finish, name in (("perf 60s cancelled", "perf status", "perf stop", "cancelled"),
                                        (f"record 60s {world}-cancelled width=256 fps=10 audio=off", "record status",
                                         "record stop", f"{world}-cancelled")):
        waiting = subprocess.Popen(launcher(game_dir) + ["--game-dir", game_dir, "--timeout", "90", start],
                                   stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
        time.sleep(2)
        run(game_dir, "stop")
        answer, _ = waiting.communicate(timeout=30)
        reason = unsupported_reason(answer, False)
        if reason:
            raise Skipped(reason)
        _, output = run(game_dir, status)
        run(game_dir, finish)
        if name not in output:
            return f"after {start} and stop, {status} answers {output.strip()}"
    return None


def check_mine_moves_on(game_dir):
    """Sand lands on the only target while the player walks to it, too far off to reach in time."""
    _, output = run(game_dir, f"{CLEAR}; {LEVEL}; server setblock 0 -60 14 minecraft:{GRASS}; "
                              f"server setblock 0 -59 14 minecraft:sand; ?mine {GRASS}; wait 5t; status; wait 5t; "
                              "status; server fill 0 -60 14 0 -59 14 minecraft:air")
    if f"err no_block {GRASS}" not in output:
        return f"counted a block that changed by itself: {output.strip()[:300]}"
    positions = [float(north_south) for north_south in re.findall(r"^ok pos \S+,(\S+)", output, re.MULTILINE)]
    # A player who has stopped still slides a little; one still walking covers a block in these 5 ticks.
    if len(positions) != 2 or abs(positions[1] - positions[0]) > 0.2:
        return f"didn't stop walking: {positions}"
    return None


# A room east of the player, 3x3x3 inside, walled, floored and roofed with the block named; 12,-60,0 is in it.
ROOM = "server fill 10 -61 -2 14 -57 2 minecraft:{} hollow"
ROOM_GONE = "server fill 10 -60 -2 14 -57 2 minecraft:air; server fill 10 -61 -2 14 -61 2 minecraft:grass_block"


def check_baritone(game_dir, installed):
    """With Baritone installed, goto digs into a closed room, mine digs to gold blocks behind its walls, a walk into
    bedrock answers no_path, and stop ends a walk, all through Baritone."""
    if not installed:
        raise Skipped("Baritone isn't installed in this run; tools/test_targets.py --baritone runs this")
    problems = []
    for script, expected in ((f"{LEVEL}; {ROOM.format('stone')}; goto 12,-60,0", r"^ok \S+s by=baritone$"),
                             (f"{LEVEL}; {ROOM.format('stone')}; server fill 11 -60 0 13 -60 0 minecraft:gold_block; "
                              "mine gold_block x3", r"^ok mine 3/3 gold_block by=baritone$"),
                             (f"{LEVEL}; {ROOM.format('bedrock')}; ?goto 12,-60,0",
                              r"^err no_path Baritone found none to 12,-60,0 by=baritone$")):
        _, output = run(game_dir, script)
        run(game_dir, ROOM_GONE)
        if not re.search(expected, output, re.MULTILINE):
            problems.append(f"{script.rsplit('; ', 1)[1]}: {output.strip()[-300:]}")
    run(game_dir, LEVEL)
    walking = subprocess.Popen(launcher(game_dir) + ["--game-dir", game_dir, "--timeout", "90", "goto 0,-60,60"],
                               stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    time.sleep(3)
    run(game_dir, "stop")
    answer, _ = walking.communicate(timeout=30)
    _, output = run(game_dir, f"status; wait 10t; status; {LEVEL}")
    positions = [[float(part) for part in place.split(",")]
                 for place in re.findall(r"^ok pos (\S+)", output, re.MULTILINE)]
    # A player who has stopped still slides a little; one still walking covers two blocks in these 10 ticks.
    if "err cancelled" not in answer or len(positions) != 2 or math.dist(*positions) > 0.2:
        problems.append(f"stop: the walk answered {answer.strip()[-200:]}, then the player was at {positions}")
    return "; ".join(problems) or None


def check_functions(game_dir, world):
    """A function without return answers ok and one with it its value; an unknown one, and a command that can't be
    parsed, answer command_failed."""
    pack = os.path.join(game_dir, "saves", world, "datapacks", "smoke")
    old = game_version(game_dir) < (1, 21)
    # 1.21 named the folder in the singular.
    functions = os.path.join(pack, "data", "smoke", "functions" if old else "function")
    os.makedirs(functions, exist_ok=True)
    # 1.20.1's format, or 1.21.1's; 1.21.11 and 26.x read a lone pack_format below their new scheme as an old pack.
    with open(os.path.join(pack, "pack.mcmeta"), "w", encoding="utf-8") as mcmeta:
        json.dump({"pack": {"description": "Blockpilot smoke test", "pack_format": 15 if old else 48}}, mcmeta)
    for name, body in (("place", "setblock 0 -60 2 minecraft:stone"), ("seven", "return 7")):
        with open(os.path.join(functions, f"{name}.mcfunction"), "w", encoding="utf-8") as function:
            function.write(body + "\n")
    run(game_dir, "server reload")
    # The server applies a reload over the ticks after it, so the functions turn up a little later.
    deadline = time.time() + 30
    while "result=7" not in run(game_dir, "?server function smoke:seven")[1]:
        if time.time() > deadline:
            return "the smoke data pack's functions didn't load"
        time.sleep(0.5)
    _, output = run(game_dir, f"{CLEAR}; server function smoke:place; block 0,-60,2 side=server; "
                              "server function smoke:seven; ?server function smoke:missing; "
                              "?server setblock 0 -60 0 minecraft:not_a_block; server setblock 0 -60 2 minecraft:air")
    # 1.20.1's game counts the commands a function ran as its result.
    ran = r"^ok result=1 success=true\n  Executed 1 command\(s\) from function 'smoke:place'$" if old \
        else r"^ok success=true\n  Running function smoke:place$"
    expected = [ran, r"^ok stone$", r"^ok result=7 success=true$",
                r"^err command_failed .*smoke:missing",
                r"^err command_failed Unknown block type 'minecraft:not_a_block'", r"^  .*<--\[HERE\]$"]
    missing = [pattern for pattern in expected if not re.search(pattern, output, re.MULTILINE)]
    return f"missing {missing}: {output.strip()[:600]}" if missing else None


class NbtByte(int):
    """An int written as an NBT byte, not an int."""


def nbt(value):
    """The tag id and payload of a value: an int as Int, an NbtByte, a str, a list of one kind, a dict as Compound."""
    if isinstance(value, NbtByte):
        return 1, struct.pack(">b", value)
    if isinstance(value, int):
        return 3, struct.pack(">i", value)
    if isinstance(value, str):
        data = value.encode("utf-8")
        return 8, struct.pack(">H", len(data)) + data
    if isinstance(value, list):
        items = [nbt(item) for item in value]
        return 9, struct.pack(">bi", items[0][0] if items else 0, len(items)) + b"".join(body for _, body in items)
    entries = [(key, *nbt(item)) for key, item in value.items()]
    return 10, b"".join(struct.pack(">b", kind) + nbt(key)[1] + body for key, kind, body in entries) + b"\x00"


def write_structure(path, data_version, size, blocks):
    """A structure template, gzipped NBT: blocks as (x, y, z, block id, its states, its block entity's data)."""
    palette = []
    placed = []
    for x, y, z, block, states, data in blocks:
        state = {"Name": block, "Properties": states}
        if state not in palette:
            palette.append(state)
        placed.append({"pos": [x, y, z], "state": palette.index(state), "nbt": data})
    _, body = nbt({"DataVersion": data_version, "size": list(size), "palette": palette, "blocks": placed,
                   "entities": []})
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with gzip.open(path, "wb") as template:
        template.write(struct.pack(">bH", 10, 0) + body)


# The data version GameTest structures are written in, from each version on, which the game upgrades from: 1.20.1's,
# 1.21.1's, 1.21.11's and 26.1.2's.
TEST_DATA_VERSION = {(1, 20): 3465, (1, 21): 3955, (1, 21, 11): 4671, (26, 1): 4790}


def test_data_version(version):
    return TEST_DATA_VERSION[max(key for key in TEST_DATA_VERSION if key <= version)]
# The file create world leaves in a save folder to mark it a test world.
TEST_MARKER = "blockpilot-test.json"


def check_gametests(game_dir, world):
    """gametest run answers a row for a test that passes and one that fails on purpose, and err no_test for a selector
    matching none: from 1.21.11 the block-based tests of a data pack, before it Blockpilot's own dev tests. The data
    pack makes the game ask about experimental settings when the world loads: join world answers for a test world and
    leaves the question on screen for any other."""
    save = os.path.join(game_dir, "saves", world)
    version = game_version(game_dir)
    # From 1.21.5 tests are data: on 1.21.11 and later a data pack's block-based tests.
    if version >= (1, 21, 11):
        pack = os.path.join(save, "datapacks", "gametests")
        os.makedirs(pack, exist_ok=True)
        with open(os.path.join(pack, "pack.mcmeta"), "w", encoding="utf-8") as mcmeta:
            json.dump({"pack": {"description": "Blockpilot smoke GameTests", "pack_format": 48}}, mcmeta)

        def test_block(x, mode, message=""):
            return (x, 0, 0, "minecraft:test_block", {"mode": mode},
                    {"id": "minecraft:test_block", "mode": mode, "message": message, "powered": NbtByte(0)})
        # The start block powers the block beside it when the test starts: accept passes, fail fails with its
        # message, and the accept block out of its reach keeps the test from failing as having none.
        for name, blocks in (("passes", [test_block(0, "start"), test_block(1, "accept")]),
                             ("fails", [test_block(0, "start"), test_block(1, "fail", "fails on purpose"),
                                        test_block(3, "accept")])):
            write_structure(os.path.join(pack, "data", "blockpilot_smoke", "structure", f"{name}.nbt"),
                            test_data_version(version), (max(block[0] for block in blocks) + 1, 1, 1), blocks)
            instance = os.path.join(pack, "data", "blockpilot_smoke", "test_instance", f"{name}.json")
            os.makedirs(os.path.dirname(instance), exist_ok=True)
            with open(instance, "w", encoding="utf-8") as test:
                json.dump({"type": "minecraft:block_based", "environment": "minecraft:default",
                           "structure": f"blockpilot_smoke:{name}", "max_ticks": 40}, test)
        # Test instances load with the world, so after the reload has taken the pack on, the world is joined again.
        # Without its marker first: the world could be a player's own, so the question stays on screen, and Escape
        # cancels it. That comes before a test world's join answers it, which NeoForge remembers for the world.
        run(game_dir, "server reload; wait 1s; leave")
        marker = os.path.join(save, TEST_MARKER)
        os.rename(marker, marker + ".away")
        try:
            _, output = run(game_dir, f"?join world {world}; screen; key escape")
        finally:
            os.rename(marker + ".away", marker)
        if not re.search(r"^err join_failed the game asks whether to back this world up", output, re.MULTILINE) \
                or "ok backup_confirm" not in output:
            return f"another world's question wasn't left on screen: {output.strip()[:400]}"
        _, output = run(game_dir, f"join world {world}")
        if f"ok joined {world} experimental=true" not in output:
            return f"didn't join the test world past the experimental-settings question: {output.strip()[-300:]}"
        selector, passed, failed = "blockpilot_smoke:*", "blockpilot_smoke:passes", "blockpilot_smoke:fails"
    else:
        write_structure(os.path.join(save, "generated", "blockpilot", "structures", "smoke_empty.nbt"),
                        test_data_version(version), (1, 1, 1), [])
        selector, passed, failed = "blockpilotsmoketests.*", "blockpilotsmoketests.passes", "blockpilotsmoketests.fails"
    # Far from where the other checks look: clearing the tests' area leaves stone in its floor on 1.21.1.
    _, output = run(game_dir, f"server as=player tp @s 1000.5 -60 1000.5 0 0; wait 10t; gametest run {selector}; "
                              f"?gametest run nothing_here:*; server test clearall; {LEVEL}")
    expected = [r"^ok passed=1 failed=1$", rf"^  {re.escape(passed)} passed$",
                rf"^  {re.escape(failed)} failed .*fails on purpose", r"^err no_test nothing_here:\*$"]
    missing = [pattern for pattern in expected if not re.search(pattern, output, re.MULTILINE)]
    return f"missing {missing}: {output.strip()[:600]}" if missing else None


def check_logs_per_world(game_dir, world):
    """Entering a world, even the one just left, starts the sound and message logs anew, so a sound and a chat line from
    before are gone."""
    def counts():
        found = [re.search(r"^ok tick=\d+ count=(\d+)", run(game_dir, query)[1], re.MULTILINE)
                 for query in ("sounds match=block.note_block.bell", "messages match=*logs-per-world*")]
        return [int(match.group(1)) if match else None for match in found]
    run(game_dir, "sound minecraft:block.note_block.bell; server say logs-per-world; wait 5t")
    before = counts()
    if None in before or min(before) < 1:
        return f"the sound and the chat line weren't logged: {before}"
    _, joined = run(game_dir, f"leave; join world {world}")
    if f"ok joined {world}" not in joined:
        return f"didn't join the world again: {joined.strip()[-300:]}"
    after = counts()
    return None if after == [0, 0] else f"the logs kept the last visit's entries: sounds, messages {after}"


def check_tick_waits(game_dir):
    """Forty wait 1t put forty client ticks between the samples around them."""
    _, output = run(game_dir, "sample status every 1t x1; " + "wait 1t; " * 40 + "sample status every 1t x1")
    ticks = [int(tick) for tick in re.findall(r"^  t\d+ c(\d+)\b", output, re.MULTILINE)]
    if len(ticks) != 2 or ticks[1] - ticks[0] != 40:
        return f"client ticks around forty wait 1t are {ticks}, not 40 apart"
    return None


def check_server_steps(game_dir):
    """Server steps run back to back: settle=1t puts them a server tick apart, settle=0t in one tick."""
    problems = []
    for settle, apart in (("1t", 1), ("0t", 0)):
        _, output = run(game_dir, "; ".join([f"server settle={settle} time query gametime"] * 3))
        times = [int(time) for time in re.findall(r"^ok result=(\d+) success=true", output, re.MULTILINE)]
        if len(times) != 3 or any(later - earlier != apart for earlier, later in zip(times, times[1:])):
            problems.append(f"game times at settle={settle} are {times}, not {apart} apart")
    return "; ".join(problems) or None


def check_video_upright(game_dir, world):
    """A recorded video is upright too; its frames come from another readback than `pixels` reads."""
    ffmpeg = shutil.which("ffmpeg")
    if not ffmpeg:
        raise Skipped("ffmpeg isn't installed")
    _, output = run(game_dir, f"{CLEAR}; {LEVEL}; option hud off; wait 3t; "
                              f"record 1s {world}-upright width=256 fps=10 audio=off; option reset")
    reason = unsupported_reason(output, False)
    if reason:
        run(game_dir, "option reset")
        raise Skipped(reason)
    video = re.search(r"^ok (.+\.mp4) ", output, re.MULTILINE)
    if not video:
        return f"no video: {output.strip()}"
    # The reply's path is absolute, or relative to the game's folder on the vanilla client.
    frame = subprocess.run([ffmpeg, "-v", "error", "-i", os.path.join(game_dir, video.group(1)), "-frames:v", "1", "-f",
                            "rawvideo", "-pix_fmt", "rgb24", "-"], capture_output=True, check=True).stdout
    row = 256 * 3
    rows = len(frame) // row
    band = rows // 5

    def mean(first, last):
        pixels = frame[first * row:last * row]
        return tuple(sum(pixels[channel::3]) / (len(pixels) / 3) for channel in range(3))

    sky, ground = mean(0, band), mean(rows - band, rows)
    return None if upright(sky, ground) else f"sky {sky}, ground {ground}"


def check_marks(game_dir, world):
    """A change right after record mark shows in the video from the frame after the one the mark names, not before
    it, and the marks file beside the video lists the mark as the reply gave it; a timed take leaves no marks file."""
    ffmpeg = shutil.which("ffmpeg")
    if not ffmpeg:
        raise Skipped("ffmpeg isn't installed")
    _, output = run(game_dir, f"{CLEAR}; {LEVEL}; option hud off; wait 3t; "
                              f"record start {world}-marks width=256 fps=20 audio=off; wait 10t; record mark hud; "
                              "option hud on; wait 10t; record stop; option reset")
    reason = unsupported_reason(output, False)
    if reason:
        run(game_dir, "option reset")
        raise Skipped(reason)
    mark = re.search(r"^ok mark hud ([\d.]+)s frame=(\d+)$", output, re.MULTILINE)
    video = re.search(r"^ok (.+\.mp4) ", output, re.MULTILINE)
    if not mark or not video:
        return f"no mark or no video: {output.strip()[:600]}"
    if not re.search(rf"^  hud {re.escape(mark.group(1))}s frame={mark.group(2)}$", output, re.MULTILINE):
        return f"record stop doesn't list the mark as it answered: {output.strip()[:600]}"
    seconds, frame = float(mark.group(1)), int(mark.group(2))
    # The reply's path is absolute, or relative to the game's folder on the vanilla client.
    path = os.path.join(game_dir, video.group(1))

    def hotbar(index):
        pixels = subprocess.run([ffmpeg, "-v", "error", "-i", path, "-vf", f"select=eq(n\\,{index})",
                                 "-frames:v", "1", "-f", "rawvideo", "-pix_fmt", "rgb24", "-"],
                                capture_output=True, check=True).stdout
        row = 256 * 3
        rows = len(pixels) // row
        band = b"".join(pixels[line * row + int(256 * 0.35) * 3:line * row + int(256 * 0.65) * 3]
                        for line in range(int(rows * 0.9), rows))
        return tuple(sum(band[channel::3]) / (len(band) / 3) for channel in range(3))

    before, after, later = hotbar(frame - 1), hotbar(frame + 1), hotbar(frame + 3)
    shown = max(abs(hidden - visible) for hidden, visible in zip(before, later))
    steady = max(abs(first - second) for first, second in zip(after, later))
    if shown <= 3 or steady > shown / 2:
        return f"hotbar means at frames {frame - 1}, {frame + 1}, {frame + 3}: {before}, {after}, {later}"
    with open(path[:-len(".mp4")] + ".marks.json", encoding="utf-8") as source:
        marks = json.load(source)
    expected = {"video": os.path.basename(path), "marks": [{"label": "hud", "seconds": seconds, "frame": frame}]}
    if marks != expected:
        return f"marks file {marks}, not {expected}"
    _, output = run(game_dir, f"record 1s {world}-unmarked width=256 fps=10 audio=off")
    timed = re.search(r"^ok (.+)\.mp4 ", output, re.MULTILINE)
    if not timed or os.path.exists(os.path.join(game_dir, timed.group(1) + ".marks.json")):
        return f"a timed take left a marks file, or no video: {output.strip()[:300]}"
    return None


# After LEVEL the player stands in block 0,-60,0, which ~dx,dy,dz counts from.
BASE = (0, -60, 0)


def camera_rows(output):
    """(client tick, camera position, camera yaw) of each row of a status sample taken while a camera is placed."""
    rows = []
    for line in output.splitlines():
        tick = re.search(r"^  t\d+ c(\d+)\b", line)
        camera = re.search(r"\bcamera (-?[\d.]+),(-?[\d.]+),(-?[\d.]+)", line)
        yaw = re.search(r"\bcam:(-?[\d.]+)/", line)
        if tick and camera:
            rows.append((int(tick.group(1)), tuple(float(value) for value in camera.groups()),
                         float(yaw.group(1)) if yaw else None))
    return rows


def at_base(dx, dy, dz):
    return BASE[0] + dx, BASE[1] + dy, BASE[2] + dz


def yaw_toward(start, end):
    """The game's yaw for looking from one point to another, horizontally."""
    return math.degrees(math.atan2(end[2] - start[2], end[0] - start[0])) - 90


def yaw_through(before, here, after):
    """The game's yaw along a curve at here: the tangent there of the circle through the three points, horizontally,
    or the chord from before to after when they lie in a line. Unlike the chord, it holds however unevenly the points
    are spaced, as rows read from frames drawn late are."""
    (ax, az), (bx, bz), (cx, cz) = ((point[0], point[2]) for point in (before, here, after))
    chord = (cx - ax, cz - az)
    determinant = 2 * (ax * (bz - cz) + bx * (cz - az) + cx * (az - bz))
    if abs(determinant) < 1e-9 * (chord[0] ** 2 + chord[1] ** 2):
        return yaw_toward(before, after)
    centre_x = ((ax * ax + az * az) * (bz - cz) + (bx * bx + bz * bz) * (cz - az) + (cx * cx + cz * cz) * (az - bz)) \
        / determinant
    centre_z = ((ax * ax + az * az) * (cx - bx) + (bx * bx + bz * bz) * (ax - cx) + (cx * cx + cz * cz) * (bx - ax)) \
        / determinant
    tangent = (centre_z - bz, bx - centre_x)
    if tangent[0] * chord[0] + tangent[1] * chord[1] < 0:
        tangent = (-tangent[0], -tangent[1])
    return yaw_toward((0, 0, 0), (tangent[0], 0, tangent[1]))


def turned(first, second):
    """The difference between two yaws, in degrees, the short way round."""
    return abs((first - second + 180) % 360 - 180)


def merge_late_frames(gaps, mean, tolerance):
    """Rows read the camera from the last frame drawn; a frame drawn late makes one gap short and the next long, which
    together are two ordinary gaps. Merges such pairs into the two halves of their sum: a gap short by more than the
    tolerance, then a long one, the two within the tolerance of two ordinary gaps, or within twice it after a frame so
    late that both are 30% off. The slower the game draws, as in software, the more often a frame is late."""
    out, index = [], 0
    while index < len(gaps):
        pair = gaps[index:index + 2]
        if len(pair) == 2 and pair[0] < (1 - tolerance) * mean and pair[1] > mean:
            very_late = pair[0] < 0.7 * mean and pair[1] > 1.3 * mean
            if abs(sum(pair) - 2 * mean) <= (2 if very_late else 1) * tolerance * mean:
                out += [sum(pair) / 2] * 2
                index += 2
                continue
        out.append(gaps[index])
        index += 1
    return out


def check_camera_move(game_dir):
    """A move takes its duration, ends at its point as camera at would, and status and screenshot then name it."""
    _, output = run(game_dir, f"{CLEAR}; {LEVEL}; camera at ~0,2,-6 look ~0,1,0; sample status every 1t x1; "
                              "camera move ~6,2,0 look ~0,1,0 over 40t; sample status every 1t x1; screenshot 64; "
                              "status; camera reset; status")
    ticks = [row[0] for row in camera_rows(output)]
    end = "{:g},{:g},{:g}".format(*at_base(6, 2, 0))
    expected = [rf"^ok camera {end} ", rf"^ok \S+\.png .*camera {end}", rf"^ok pos .*\bcamera {end}"]
    missing = [pattern for pattern in expected if not re.search(pattern, output, re.MULTILINE)]
    statuses = [line for line in output.splitlines() if line.startswith("ok pos ")]
    if len(ticks) != 2 or not 40 <= ticks[1] - ticks[0] <= 44 or missing or " camera " in statuses[-1]:
        return f"client ticks {ticks}, missing {missing}: {output.strip()[:800]}"
    return None


def check_camera_easing(game_dir):
    """Halfway through a move the camera is about halfway along with linear and inout, short of it with in and past
    it with out. A row repeating the one before read a frame the game drew late, and the next row from a new frame
    stands in for it."""
    problems = []
    for ease, low, high in (("linear", 4, 6.5), ("inout", 4, 6.5), ("in", 0, 4.5), ("out", 5.5, 10)):
        _, output = run(game_dir, f"{CLEAR}; {LEVEL}; camera at ~0,2,0; sample status every 1t while camera move "
                                  f"~10,2,0 over 40t ease={ease}; camera reset")
        rows = camera_rows(output)
        fresh = [row for previous, row in zip(rows, rows[1:]) if row[0] - rows[0][0] >= 20 and row[1] != previous[1]]
        along = fresh[0][1][0] - rows[0][1][0] if fresh else None
        if along is None or not low <= along <= high:
            problems.append(f"{ease}: {along} blocks along at 20 ticks")
    return "; ".join(problems) or None


def check_camera_curve(game_dir):
    """A curved move passes its via point without a corner, at an even speed along the path."""
    # Every tick, so a row comes within half a block of the via point; rows two ticks apart for the turns.
    _, output = run(game_dir, f"{CLEAR}; {LEVEL}; camera at ~0,2,0; sample status every 1t while camera move "
                              "~10,2,10 via ~10,2,0 over 40t; camera reset")
    points = [row[1] for row in camera_rows(output)]
    via = at_base(10, 2, 0)
    nearest = min((math.dist(point, via) for point in points), default=None)
    pairs = points[::2]
    steps = [(first, second) for first, second in zip(pairs, pairs[1:]) if math.dist(first, second) > 0.05]
    turns = [turned(yaw_toward(*one), yaw_toward(*other)) for one, other in zip(steps, steps[1:])]
    # A smooth curve through a right-angled corner turns most of the quarter circle near it, about 30 degrees a row pair
    # here depending on where the rows fall; a corner would turn 90 at once.
    if nearest is None or nearest > 0.5 or not turns or max(turns) >= 45:
        return f"nearest {nearest} to the via point, turns {[round(turn) for turn in turns]}"
    _, output = run(game_dir, f"{CLEAR}; {LEVEL}; camera at ~0,2,0; sample status every 4t while camera move "
                              "~12,2,0 via ~2,2,0 over 40t; camera reset")
    points = [row[1] for row in camera_rows(output)]
    gaps = [math.dist(first, second) for first, second in zip(points, points[1:])]
    mean = sum(gaps) / len(gaps) if gaps else 0
    even = 0.15
    # A last row from a late frame has no row after it to count with; a slow stretch anywhere else still fails.
    if len(gaps) > 2 and gaps[-1] < (1 - even) * mean:
        gaps = gaps[:-1]
        mean = sum(gaps) / len(gaps)
    gaps = merge_late_frames(gaps, mean, even)
    if not gaps or any(abs(gap - mean) > even * mean for gap in gaps):
        return f"gaps between rows {[round(gap, 2) for gap in gaps]}"
    return None


def check_camera_looks_ahead(game_dir):
    """look ahead faces the way the camera travels from a fifth of the way on, and keeps that way at the end. The way
    through a row is the curve's tangent at the row, from the rows either side (yaw_through)."""
    _, output = run(game_dir, f"{CLEAR}; {LEVEL}; camera at ~0,2,0; sample status every 2t while camera move "
                              "~10,2,10 via ~10,2,0 look ahead over 40t; screenshot 64; status; camera reset")
    rows = camera_rows(output)
    off = [round(turned(rows[index][2], yaw_through(rows[index - 1][1], rows[index][1], rows[index + 1][1])))
           for index in range(max(1, len(rows) // 5), len(rows) - 1)
           if math.dist(rows[index - 1][1], rows[index + 1][1]) > 0.05]
    final = re.findall(r"^ok pos .*\bcam:(-?[\d.]+)/", output, re.MULTILINE)
    last = yaw_toward(rows[-2][1], rows[-1][1]) if len(rows) > 2 else None
    if not off or max(off) > 10 or not final or last is None or turned(float(final[-1]), last) > 10:
        return f"yaw off the way of travel by {off}, at the end {final} against {last}"
    return None


def check_camera_move_background(game_dir):
    """A camera move in the background answers at once and lets server steps run while it moves, camera wait answers
    at its end and at once with nothing moving, and stop from another call leaves it where it was drawn."""
    _, output = run(game_dir, f"{CLEAR}; {LEVEL}; camera at ~0,2,0; camera move ~10,2,0 over 40t background; "
                              "server settle=1t time query gametime; wait 20t; status; camera wait; camera wait")
    along = [float(x) - BASE[0] for x in re.findall(r"^ok pos .*?\bcamera (-?[\d.]+),", output, re.MULTILINE)]
    end = "{:g},{:g},{:g}".format(*at_base(10, 2, 0))
    waits = re.findall(rf"^ok camera {end} ", output, re.MULTILINE)
    if not re.search(r"^ok moving over=40t$", output, re.MULTILINE) or len(along) != 1 or not 4 <= along[0] <= 6.5 \
            or len(waits) != 2:
        return f"halfway {along}, {len(waits)} waits at the end: {output.strip()[:700]}"
    run(game_dir, "camera at ~0,2,0; camera move ~10,2,0 over 40t background; wait 20t")
    run(game_dir, "stop")
    _, output = run(game_dir, "status; wait 20t; status; camera reset")
    stopped = [float(x) - BASE[0] for x in re.findall(r"^ok pos .*?\bcamera (-?[\d.]+),", output, re.MULTILINE)]
    # Where it stops depends on how soon the second call's stop arrives; that it stays is what counts.
    if len(stopped) != 2 or stopped[0] != stopped[1] or not 2 <= stopped[0] <= 9.5:
        return f"after stop the camera is at {stopped} blocks along"
    return None


def check_camera_move_recording(game_dir, world):
    """A move recorded at 30 fps is in a new place in nearly every frame, where camera at once a tick repeats a third
    of them: consecutive frames' ground, with the clouds off, barely differs only where the camera stood still. 30 fps,
    not more, so that a game drawn in software keeps up."""
    ffmpeg = shutil.which("ffmpeg")
    if not ffmpeg:
        raise Skipped("ffmpeg isn't installed")
    _, output = run(game_dir, f"{CLEAR}; {LEVEL}; option enableVsync off; option renderClouds off; camera at ~0,2,0; "
                              f"record start {world}-move width=256 fps=30 audio=off; "
                              "camera move ~10,2,10 via ~10,2,0 look ahead over 2s; record stop; camera reset; "
                              "option reset")
    reason = unsupported_reason(output, False)
    if reason:
        run(game_dir, "camera reset; option reset")
        raise Skipped(reason)
    video = re.search(r"^ok (.+\.mp4) \S+ (\d+)x(\d+) \S+ frames=(\d+) repeated=(\d+)", output, re.MULTILINE)
    if not video:
        return f"no video: {output.strip()[:600]}"
    width, height, frames, repeated = (int(value) for value in video.groups()[1:])
    if repeated * 5 > frames:
        raise Skipped(f"the game rendered too slowly to tell: {repeated} of {frames} frames repeat in the recording")
    # An even height, as 4:2:0 video crops to one.
    rows = height // 2 & ~1
    ground = subprocess.run([ffmpeg, "-v", "error", "-i", os.path.join(game_dir, video.group(1)), "-vf",
                             f"crop={width}:{rows}:0:{height - rows},format=gray", "-f", "rawvideo", "-"],
                            capture_output=True, check=True).stdout
    size = width * rows
    images = [ground[offset:offset + size] for offset in range(0, len(ground) - size + 1, size)]
    differences = [sum(abs(one - other) for one, other in zip(first, second)) / size
                   for first, second in zip(images, images[1:])]
    still = sum(1 for difference in differences if difference < 0.5)
    return None if still * 5 < len(differences) else f"{still} of {len(differences)} frames barely differ"


def check_camera_away(game_dir):
    """A camera away from the player has the server load, send and play the world around it, while the player waits;
    a move keeps it away, and a reset brings the world back to the player."""
    _, output = run(game_dir, f"{CLEAR}; {LEVEL}; camera at 1000,-58,0 look 1000,-60,5 away; settle timeout=60s; "
                              "block 1000,-61,0; status; wait 1s; status; server give @p minecraft:stone; wait 5t; "
                              "inv; "
                              "server playsound minecraft:block.bell.use master @a 1000 -60 5; "
                              "server summon minecraft:lightning_bolt 1000 -60 5; "
                              "server particle minecraft:flame 1000 -59 5 0.2 0.2 0.2 0 50; wait 1s; "
                              "sounds since=3s; particles since=3s; "
                              "camera move 1400,-58,0 look 1400,-60,5 over 40t; settle timeout=60s; block 1400,-61,0; "
                              "camera reset; settle timeout=60s; block ~0,-1,0; move forward 1; status; "
                              "?server kill @e[type=minecraft:item]")
    expected = [r"^ok camera 1000,-58,0 in:air", r"^ok settled=", r"^ok grass_block", r"\bstone x1\b",
                r"block\.bell\.use master ~1000(\.\d+)?,-?\d+(\.\d+)?,5(\.\d+)? ", r"entity\.lightning_bolt\.thunder",
                r"\bflame\b", r"^ok camera 1400,-58,0 "]
    missing = [pattern for pattern in expected if not re.search(pattern, output, re.MULTILINE)]
    positions = re.findall(r"^ok pos (\S+) ", output, re.MULTILINE)
    blocks = re.findall(r"^ok (grass_block)\b", output, re.MULTILINE)
    # The player stands still while the camera is away, and walks again after the reset.
    still = len(positions) == 3 and positions[0] == positions[1] != positions[2]
    if missing or not still or len(blocks) != 3:
        return f"missing {missing}, positions {positions}, grass {len(blocks)}: {output.strip()[:900]}"
    return None


def check_entity_names(game_dir):
    """@<name> takes the entity tagged or called so wherever e<id> is taken, on the client and on the server thread,
    the nearest of several to where the server takes the player to be, and fails when no entity has the name."""
    # A custom name is a text component: JSON inside an NBT string before 26.1, SNBT from then on.
    # 1.21.5 made names in entity data text components rather than JSON in a string.
    daisy = "'\"Daisy\"'" if game_version(game_dir) < (1, 21, 11) else '"Daisy"'
    _, output = run(game_dir, f"{CLEAR}; {LEVEL}; ?server kill @e[type=minecraft:cow]; "
                              "server as=player summon minecraft:cow ~3 ~ ~4 {Tags:[\"star\"],NoAI:1b}; "
                              "server as=player summon minecraft:cow ~3 ~ ~40 {Tags:[\"star\"],NoAI:1b}; "
                              f"server as=player summon minecraft:cow ~2 ~ ~ {{CustomName:{daisy},NoAI:1b}}; wait 5t; "
                              "entity @star; nbt @star path=Pos; entity @daisy; camera at ~0,2,-6 look @star; status; "
                              "camera reset; face @daisy; camera at ~3,2,36 look ~3,0,40 away; entity @star; "
                              "nbt @star path=Pos; camera reset; ?entity @nobody; server kill @e[type=minecraft:cow]")
    cows = re.findall(r"^ok cow (e\d+) pos (-?[\d.]+),(-?[\d.]+),(-?[\d.]+)", output, re.MULTILINE)
    served = re.findall(r"^ok \[(-?[\d.]+)d, ?(-?[\d.]+)d, ?(-?[\d.]+)d\]", output, re.MULTILINE)
    camera = re.search(r"^ok camera (-?[\d.]+),(-?[\d.]+),(-?[\d.]+)", output, re.MULTILINE)
    yaw = re.search(r"^ok pos .*\bcam:(-?[\d.]+)/", output, re.MULTILINE)
    if len(cows) != 3 or len(served) != 2 or not camera or not yaw:
        return f"no stars, daisy, camera or camera yaw: {output.strip()[:900]}"
    near, far = (tuple(float(value) for value in cow[1:]) for cow in (cows[0], cows[2]))
    toward = yaw_toward(tuple(float(value) for value in camera.groups()), near)
    expected = [rf"^ok aim:cow {cows[1][0]}\b", r"^err no_entity @nobody$"]
    missing = [pattern for pattern in expected if not re.search(pattern, output, re.MULTILINE)]
    # From the player the star at ~3,0,4 is the nearer, from the camera away at ~3,2,36 the one at ~3,0,40; the client
    # and the server thread pick the same.
    places = [(near[0], near[2]), (float(served[0][0]), float(served[0][2])), (far[0], far[2]),
              (float(served[1][0]), float(served[1][2]))]
    wanted = [(3.5, 4.5), (3.5, 4.5), (3.5, 40.5), (3.5, 40.5)]
    if missing or any(math.dist(place, want) > 0.5 for place, want in zip(places, wanted)) \
            or turned(float(yaw.group(1)), toward) > 5:
        return f"missing {missing}, stars at {places}, camera yaw {yaw.group(1)} for {toward:.1f}: " \
               f"{output.strip()[:900]}"
    return None


# An armor stand to follow and circle, facing south; it turns its body to its yaw when teleported.
WALKER = "server as=player summon minecraft:armor_stand ~3 ~ ~3 {Tags:[\"walker\"],NoGravity:1b,Rotation:[0f,0f]}"
MOVE_WALKER = "server execute as @e[tag=walker] at @s run tp @s"


def camera_positions(output):
    """The camera position of each `status` reply, in order."""
    return [tuple(float(value) for value in match) for match in
            re.findall(r"^ok pos .*\bcamera (-?[\d.]+),(-?[\d.]+),(-?[\d.]+)", output, re.MULTILINE)]


def entity_positions(output):
    """The position of each `entity` reply, in order."""
    return [tuple(float(value) for value in match) for match in
            re.findall(r"^ok \S+ e\d+ pos (-?[\d.]+),(-?[\d.]+),(-?[\d.]+)", output, re.MULTILINE)]


def check_camera_follow(game_dir):
    """A follow keeps its offset from a moved entity, with turning stays behind it as it turns, outlasts a stop, stays
    where it was when the entity goes, and refuses an absolute offset."""
    _, output = run(game_dir, f"{CLEAR}; {LEVEL}; ?server kill @e[tag=walker]; {WALKER}; wait 5t; "
                              f"camera at ~0,2,-6 look @walker; camera follow @walker; {MOVE_WALKER} ~8 ~ ~; wait 10t; "
                              f"status; camera follow @walker at ~0,2,-6 turning; {MOVE_WALKER} ~ ~ ~ 90 0; wait 3s; "
                              "entity @walker; status; ?camera follow @walker at 0,2,-6")
    run(game_dir, "stop")
    _, after = run(game_dir, f"{MOVE_WALKER} ~8 ~ ~; wait 10t; entity @walker; status; server kill @e[tag=walker]; "
                             "wait 5t; status; wait 1s; status; camera reset")
    cameras, walkers = camera_positions(output), entity_positions(output)
    later, moved = camera_positions(after), entity_positions(after)
    problems = []
    # The client eases a teleported entity in over a few ticks, and status reads the last frame drawn.
    if not cameras or math.dist(cameras[0], at_base(8, 2, -6)) > 0.25:
        problems.append(f"the framing didn't go along: {cameras[:1]}")
    behind = (6, 2, 0)
    if len(cameras) < 2 or not walkers or math.dist(tuple(c - w for c, w in zip(cameras[1], walkers[0])), behind) > 0.3:
        problems.append(f"not behind the turned walker: camera {cameras[1:2]}, walker {walkers[:1]}")
    if not re.search(r"^err syntax follow at takes an offset", output, re.MULTILINE):
        problems.append("an absolute offset wasn't refused")
    if len(later) != 3 or not moved or math.dist(tuple(c - w for c, w in zip(later[0], moved[0])), behind) > 0.3:
        problems.append(f"stop ended the follow: camera {later[:1]}, walker {moved[:1]}")
    elif later[1] != later[2]:
        problems.append(f"the camera moved after the walker went: {later[1:]}")
    _, away = run(game_dir, "camera at 1000,-58,0 look 1000,-60,5 away; "
                            "server summon minecraft:armor_stand 1000 -60 5 {Tags:[\"far\"],NoGravity:1b}; wait 5t; "
                            "camera follow @far; server execute as @e[tag=far] at @s run tp @s ~40 ~ ~; "
                            "settle timeout=60s; block 1040,-61,0; status; wait 1s; status; server kill @e[tag=far]; "
                            "camera reset; settle timeout=60s")
    positions = re.findall(r"^ok pos (\S+) ", away, re.MULTILINE)
    if not re.search(r"^ok grass_block", away, re.MULTILINE) or len(positions) != 2 or positions[0] != positions[1]:
        problems.append("a follow far away didn't keep the world with the camera, or the player moved")
    return "; ".join(problems) + f": {(output + after + away).strip()[:900]}" if problems else None


def across(first, second):
    """The horizontal distance between two points."""
    return math.dist((first[0], first[2]), (second[0], second[2]))


def check_camera_orbit(game_dir):
    """An orbit keeps its distance and height round a point and ends by its angle, goes along with a moving entity,
    holds when its entity goes, runs in the background for camera wait, and keeps a camera placed away away."""
    centre = at_base(0, 1, 0)
    _, output = run(game_dir, f"{CLEAR}; {LEVEL}; camera at ~0,3,-6 look ~0,1,0; sample status every 5t while camera "
                              "orbit ~0,1,0 by 90 over 40t; status; camera at ~0,3,-6 look ~0,1,0; camera orbit "
                              "~0,1,0 by -90 over 40t background; camera wait")
    rows = [row[1] for row in camera_rows(output)]
    problems = []
    off = [round(across(row, centre), 2) for row in rows if abs(across(row, centre) - 6) > 0.2
           or abs(row[1] - centre[1] - 2) > 0.2]
    ends = camera_positions(output)
    if len(rows) < 5 or off or not ends or math.dist(ends[-1], at_base(-6, 3, 0)) > 0.1:
        problems.append(f"rows off the circle {off}, end {ends[-1:]}")
    waited = re.search(r"^ok moving over=40t\n.*?^ok camera (-?[\d.]+),(-?[\d.]+),(-?[\d.]+)", output,
                       re.MULTILINE | re.DOTALL)
    if not waited or math.dist(tuple(float(value) for value in waited.groups()), at_base(6, 3, 0)) > 0.1:
        problems.append("camera wait didn't answer at the background orbit's end")
    _, moving = run(game_dir, f"?server kill @e[tag=walker]; {WALKER}; wait 5t; "
                              "camera at ~3,2,-3 look @walker; entity @walker; status; "
                              f"camera orbit @walker over 40t background; {MOVE_WALKER} ~10 ~ ~; wait 20t; "
                              "entity @walker; status; camera wait; camera orbit @walker over 40t background; "
                              "server kill @e[tag=walker]; wait 5t; status; wait 10t; status; camera wait; "
                              "camera reset")
    cameras, walkers = camera_positions(moving), entity_positions(moving)
    if len(cameras) != 4 or len(walkers) != 2 \
            or abs(across(cameras[1], walkers[1]) - across(cameras[0], walkers[0])) > 0.3:
        problems.append(f"the orbit didn't go along with the walker: cameras {cameras[:2]}, walkers {walkers}: "
                        f"{moving.strip()[:700]}")
    elif cameras[2] != cameras[3] or len(re.findall(r"^ok camera ", moving, re.MULTILINE)) != 3:
        problems.append(f"the orbit didn't hold when its walker went: {cameras[2:]}")
    _, away = run(game_dir, "camera at 1000,-58,0 look 1000,-60,5 away; camera orbit 1000,-60,5 by 180 over 40t; "
                            "settle timeout=60s; block 1000,-61,10; status; wait 1s; status; camera reset; "
                            "settle timeout=60s")
    positions = re.findall(r"^ok pos (\S+) ", away, re.MULTILINE)
    if not re.search(r"^ok camera 1000,-58,10 ", away, re.MULTILINE) \
            or not re.search(r"^ok grass_block", away, re.MULTILINE) or len(positions) != 2 \
            or positions[0] != positions[1]:
        problems.append("an orbit far away didn't keep the world with the camera, or the player moved")
    return "; ".join(problems) + f": {(output + moving + away).strip()[:900]}" if problems else None


def check_camera_lens(game_dir):
    """A narrow field of view fills the frame with a far block, a roll puts the sky to one side, a move glides the field
    of view, a placement keeps it, and a value out of range is refused before anything changes."""
    # A red block 20 blocks in front of the camera, looked at through its centre.
    _, output = run(game_dir, f"{CLEAR}; {LEVEL}; option hud off; server as=player setblock ~5 ~1 ~5 "
                              "minecraft:red_concrete; wait 5t; camera at ~5.5,1.5,-14.5 look ~5.5,1.5,5.5; wait 3t; "
                              "pixels wide=0.48,0.48,0.52,0.52; camera at ~5.5,1.5,-14.5 look ~5.5,1.5,5.5 fov=10; "
                              "wait 3t; pixels narrow=0.48,0.48,0.52,0.52; "
                              "camera at ~0,2,0 look ~0,2,10 fov=70 roll=90; wait 3t; "
                              "pixels left=0,0.4,0.2,0.6 right=0.8,0.4,1,0.6; status; "
                              "camera at ~0,2,-6 look ~0,1,0 fov=70 roll=0; "
                              "sample status every 10t while camera move ~0,2,-8 look ~0,1,0 over 40t fov=30; "
                              "camera at ~4,2,-6; status; ?camera at ~0,2,0 fov=200; ?camera reset roll=10; "
                              "camera reset; server as=player setblock ~5 ~1 ~5 minecraft:air; option reset")
    problems = []
    wide, narrow = means(output, "wide"), means(output, "narrow")
    if not (narrow[0] > narrow[1] + 60 and narrow[0] > narrow[2] + 60) or wide[0] > wide[2] + 60:
        problems.append(f"the narrow view {narrow} isn't red, or the wide one {wide} is")
    left, right = means(output, "left"), means(output, "right")
    rolled = re.search(r"^ok pos .*\bcam:-?[\d.]+/-?[\d.]+/(-?[\d.]+)", output, re.MULTILINE)
    if not (left[2] > left[0] and right[0] > right[2]) or not rolled or abs(float(rolled.group(1)) - 90) > 0.5:
        problems.append(f"rolled: left {left}, right {right}, roll {rolled.group(1) if rolled else None}")
    fovs = [float(value) for value in re.findall(r"^  t\d+ c\d+ .*\bcam_fov=([\d.]+)", output, re.MULTILINE)]
    if len(fovs) < 4 or abs(fovs[2] - 50) > 4 or abs(fovs[-1] - 30) > 1:
        problems.append(f"the zoom along the move read {fovs}")
    if not re.search(r"^ok pos .*\bcam_fov=30\b", output, re.MULTILINE):
        problems.append("the next placement didn't keep the field of view")
    if not re.search(r"^err syntax fov= takes 1 to 170 degrees, not 200", output, re.MULTILINE) \
            or not re.search(r"^err syntax fov= and roll= are for camera at, move, follow and orbit", output,
                             re.MULTILINE):
        problems.append("a value out of range or an option on reset wasn't refused")
    return "; ".join(problems) + f": {output.strip()[:900]}" if problems else None


def check_surroundings_of_an_away_camera(game_dir):
    """With a camera placed away, look and find search around it, and after a reset around the player again."""
    _, setup = run(game_dir, f"{CLEAR}; {LEVEL}; ?server kill @e[type=minecraft:armor_stand]; "
                             "camera at 1000,-58,0 look 1000,-60,5 away; settle timeout=60s; "
                             "server summon minecraft:armor_stand 1003 -60 3; wait 5t; look 8")
    _, found = run(game_dir, "find armor_stand 8")
    _, grass = run(game_dir, "find grass_block 3 1", as_json=True)
    # The armor stand goes while the camera is by it, as only loaded entities can be killed.
    _, back = run(game_dir, "?server kill @e[type=minecraft:armor_stand]; camera reset; settle timeout=60s; look 8")
    problems = []
    if not re.search(r"^  armor_stand e\d+ ~1003\.5,0,3\.5\b", setup, re.MULTILINE):
        problems.append(f"look by the camera didn't list the armor stand: {setup.strip()[-400:]}")
    if not re.search(r"^ok\n  armor_stand e\d+ ", found, re.MULTILINE):
        problems.append(f"find by the camera didn't list the armor stand first: {found.strip()[:300]}")
    rows = json.loads(grass.splitlines()[0])["results"][0].get("rows", []) if grass.startswith("{") else []
    if not rows or rows[0].get("at_abs") != [1000, -61, 0]:
        problems.append(f"find grass_block didn't name the block under the camera: {grass.strip()[:300]}")
    if re.search(r"^  armor_stand e\d+ ", back, re.MULTILINE):
        problems.append("look after the reset still listed the armor stand")
    return "; ".join(problems) or None


def check_slow_motion(game_dir, world):
    """At a quarter of the tick rate a take shows a quarter as much of the game, a camera move takes four times as
    long on the wall clock, and a mark after a wait lands as far into the video as the wait took."""
    _, output = run(game_dir, f"{CLEAR}; {LEVEL}; tick rate 5; tick query; record 2s {world}-slow fps=20 audio=off; "
                              "tick query; tick rate 20")
    lines = output.splitlines()
    take = next((index for index, line in enumerate(lines) if re.match(r"^ok \S+\.mp4 ", line)), None)
    before = [line for line in lines[:take] if line.startswith("ok tick=")] if take is not None else []
    after = [line for line in lines[take + 1:] if line.startswith("ok tick=")] if take is not None else []
    ticks = [int(re.match(r"ok tick=(\d+)", line).group(1)) for line in before[-1:] + after[:1]]
    video = re.search(r"^ok \S+\.mp4 \S+ \S+ 20fps frames=(\d+) repeated=(\d+)", output, re.MULTILINE)
    started = time.monotonic()
    _, moved = run(game_dir, "tick rate 5; camera at ~0,2,0; camera move ~4,2,0 over 20t; tick rate 20; camera reset")
    took = time.monotonic() - started
    _, marked = run(game_dir, f"tick rate 5; record start {world}-slowmark fps=20 audio=off; wait 20t; "
                              "record mark after; record stop; tick rate 20")
    mark = re.search(r"^ok mark after ([\d.]+)s ", marked, re.MULTILINE)
    problems = []
    if len(ticks) != 2 or not 5 <= ticks[1] - ticks[0] <= 20:
        problems.append(f"the game advanced {ticks} over the take")
    if not video or not 30 <= int(video.group(1)) <= 60 or int(video.group(2)) * 5 > int(video.group(1)):
        problems.append(f"the take: {video.groups() if video else output.strip()[:300]}")
    if not 3.5 <= took <= 8 or "ok camera 4," not in moved:
        problems.append(f"a 20-tick move at tick rate 5 took {took:.1f}s: {moved.strip()[:300]}")
    if not mark or not 3.5 <= float(mark.group(1)) <= 8:
        problems.append(f"the mark after wait 20t: {marked.strip()[:300]}")
    return "; ".join(problems) or None


def check_record_script(game_dir, world):
    """record script films to the end of its script, ends at a failing step, and ends when another call stops the
    script, each time saving the take and reporting it as an event of the script's reply."""
    _, filmed = run(game_dir, f"record script {world}-script fps=20 audio=off; wait 20t")
    _, failing = run(game_dir, f"record script {world}-script2 fps=20 audio=off; wait 10t; entity e999999; wait 10t")
    _, between = run(game_dir, "record status")
    taken = {}
    take = threading.Thread(target=lambda: taken.update(
        out=run(game_dir, f"record script {world}-script3 audio=off; wait 200t")[1]))
    take.start()
    time.sleep(3)
    run(game_dir, "stop")
    take.join(120)
    _, after = run(game_dir, "record status")
    stopped = taken.get("out", "")
    _, earlier = run(game_dir, f"record script {world}-script4 audio=off; wait 10t; record stop; "
                               f"record start {world}-script5 audio=off")
    _, still = run(game_dir, "record status; record stop")
    problems = []
    length = re.search(rf"^! record \S*{re.escape(world)}-script\.mp4 ([\d.]+)s ", filmed, re.MULTILINE)
    if not re.search(rf"^ok recording {re.escape(world)}-script\b", filmed, re.MULTILINE) or not length \
            or not 0.5 <= float(length.group(1)) <= 3:
        problems.append(f"filmed to the end: {filmed.strip()[:400]}")
    if not re.search(r"^err no_entity e999999", failing, re.MULTILINE) \
            or not re.search(rf"^! record \S*{re.escape(world)}-script2\.mp4 ", failing, re.MULTILINE) \
            or "no recording running" not in between:
        problems.append(f"a failing step: {failing.strip()[:400]} / {between.strip()}")
    if not re.search(r"^err cancelled", stopped, re.MULTILINE) \
            or not re.search(rf"^! record \S*{re.escape(world)}-script3\.mp4 ", stopped, re.MULTILINE) \
            or "no recording running" not in after:
        problems.append(f"stopped from another call: {stopped.strip()[:400]} / {after.strip()}")
    # A take the script stopped itself leaves the recording started after it running.
    if not re.search(rf"^ok \S*{re.escape(world)}-script4\.mp4 ", earlier, re.MULTILINE) or "! record" in earlier \
            or not re.search(rf"^ok {re.escape(world)}-script5 ", still, re.MULTILINE):
        problems.append(f"stopped by the script: {earlier.strip()[:400]} / {still.strip()[:200]}")
    return "; ".join(problems) or None


def write_shapes(game_dir, path):
    """Writes one line per query, unless a reply couldn't be read, as then they can't be compared with another target's;
    returns how many couldn't."""
    lines = []
    unreadable = 0
    # Replies have the shape of what is near and aimed at, which earlier steps leave or don't. A chest under the
    # crosshair, an item and a living entity bring out all their fields. There may be nothing to kill, hence the ?.
    run(game_dir, "?server kill @e[type=!minecraft:player]")
    code, output = run(game_dir, f"{CLEAR}; {AIMED}; server as=player setblock ~ ~ ~2 minecraft:chest; "
                                 "server as=player summon minecraft:armor_stand ~2 ~ ~; "
                                 "server as=player summon minecraft:item ~-2 ~ ~ "
                                 f"{{PickupDelay:32767,Item:{{id:\"minecraft:stone\",{COUNT}:1}}}}; wait 5t")
    if code != 0:
        print(f"FAIL json shapes: the scene for them failed: {output.strip()[:300]}")
        return 1
    for script in SHAPE_SCRIPTS:
        _, output = run(game_dir, script, as_json=True)
        try:
            reply = json.loads(output.splitlines()[0])
            # A client that lost its world still answers, but what it says has nothing to compare.
            if script != SHAPE_OF_AN_ERROR and not all(result["ok"] for result in reply["results"]):
                raise ValueError("a query failed")
            lines.append(f"{script} => " + " | ".join(shape(result) for result in reply["results"]))
        except (ValueError, IndexError, KeyError):
            unreadable += 1
            lines.append(f"{script} => unreadable: {output.strip()}")
    if unreadable:
        print(f"FAIL json shapes: {unreadable} of {len(lines)} unreadable, the first: "
              + next(line for line in lines if " => unreadable: " in line)[:300])
        return unreadable
    with open(path, "w", encoding="utf-8") as handle:
        handle.write("\n".join(lines) + "\n")
    print(f"pass json shapes: {len(lines)} written to {path}")
    return 0


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--game-dir", default="run")
    parser.add_argument("--json", action="store_true", help="run the steps in JSON mode and check reply structure")
    parser.add_argument("--shapes", help="also write the JSON shapes of a set of queries to this file")
    parser.add_argument("--baritone", action="store_true",
                        help="Baritone is installed in this game: run only the check of walks through it")
    args = parser.parse_args()
    use_names_of(game_version(args.game_dir))
    world = f"smoke-{int(time.time())}"
    give = "; ".join(f"server give @p minecraft:{item}" for item in
                     ["oak_log 4", "iron_sword", "shield", "iron_helmet", "cobblestone 16"])
    steps = [
        ("create a test world", f"create world {world} flat peaceful rule:doDaylightCycle=false",
         [r"ok joined " + world]),
        ("status", "wait 1s; status", [r"ok pos -?\d", r"overworld"]),
        ("help", "help; help goto; help errors; help server; help wait; help sound; help record",
         [r"movement:", r"goto <target>", r"no_item", r"function without return, answers ok success=true",
          r"the step after wait <N>t runs N ticks after", r"so settle=1t spaces them one server tick apart",
          r"plays a sound event on this client only", r"record mark <label>: notes the video time"]),
        ("give items", give + "; inv", [r"oak_log x4", r"iron_sword", r"shield"]),
        ("craft in the 2x2 grid", "craft oak_planks x8; craft crafting_table", [r"craft 8 oak_planks",
                                                                                r"craft 1 crafting_table"]),
        ("place and read a block", "place crafting_table ~2,0,0; block ~2,0,0", [r"ok crafting_table"]),
        # Regression: place aimed at the top of the carpet's cell, in the air above it: "cannot see the face".
        # The wait lets the place reach the server first: with the carpet gone, it would land in the carpet's cell.
        ("place on a thin block", "server as=player setblock ~0 ~ ~2 minecraft:white_carpet; "
                                  "place cobblestone ~0,1,2; wait 5t; block ~0,1,2 side=server; "
                                  "server as=player fill ~0 ~ ~2 ~0 ~1 ~2 minecraft:air", [r"^ok cobblestone$"]),
        # Regression: the crosshair met the stone's near face, and the click put the block there, on the wrong side.
        ("place against a face out of sight", "server as=player setblock ~2 ~2 ~ minecraft:stone; wait 5t; "
                                              "?place cobblestone ~3,2,0; find cobblestone 6; "
                                              "server as=player fill ~1 ~2 ~ ~2 ~2 ~ minecraft:air",
         [r"err out_of_reach cannot see the face to place against; the crosshair hits stone ~2,2,0 on its west face",
          r"^ok none"], True),
        ("craft at a table", "craft furnace", [r"craft 1 furnace"]),
        ("mine", "select iron_sword; mine ~2,0,0; block ~2,0,0", [r"mine 1/1 crafting_table", r"ok air"]),
        ("mine names what is in the way", "server as=player setblock ~ ~ ~2 minecraft:stone; "
                                          "server as=player setblock ~ ~ ~3 minecraft:cobblestone; ?mine ~0,0,3; "
                                          "server as=player fill ~ ~ ~2 ~ ~ ~3 minecraft:air",
         [r"err out_of_reach ~0,0,3 is behind stone ~0,0,2"], True),
        ("mine finds nothing in an empty cell", "?mine ~0,0,3", [r"err no_block ~0,0,3 is air"], True),
        # Regression: mine took a block something else changed as mined. Sand lands on the grass while it tries to aim.
        ("mine doesn't count a block that changed by itself",
         f"server as=player setblock ~ ~ ~2 minecraft:stone; server as=player setblock ~ ~ ~3 minecraft:{GRASS}; "
         "server as=player setblock ~ ~1 ~3 minecraft:sand; ?mine ~0,0,3; wait 15t; "
         "server as=player fill ~ ~ ~2 ~ ~1 ~3 minecraft:air",
         [r"err out_of_reach ~0,0,3 is behind stone ~0,0,2"], True),
        # Regression: mine aimed at the middle of the carpet's cell, passed over it and hit the ground behind.
        ("mine a thin block", "server as=player setblock ~ ~ ~2 minecraft:white_carpet; mine ~0,0,2; block ~0,0,2",
         [r"mine 1/1 white_carpet", r"ok air"]),
        # Without Baritone, the walk closer to a block out of sight finds no path, and the block is passed over.
        ("mine by type passes over a block sealed in stone",
         "server as=player fill ~2 ~ ~-1 ~4 ~2 ~1 minecraft:stone; "
         "server as=player setblock ~3 ~1 ~ minecraft:gold_block; ?mine gold_block; "
         "server as=player fill ~2 ~ ~-1 ~4 ~2 ~1 minecraft:air", [r"err no_block gold_block"], True),
        # place takes the first neighbour, below, above, north, south, west, east, whose face is in sight. A hole's
        # floor is hidden behind the ground in front of it, so its far side takes the block.
        ("place fills a hole against its far side",
         "server as=player setblock ~ ~-1 ~2 minecraft:air; wait 5t; ?place cobblestone ~0,-1,2; wait 5t; "
         "block ~0,-1,2 side=server; server as=player setblock ~ ~-1 ~2 minecraft:grass_block",
         [r"^ok cobblestone$"]),
        ("place above the eyes against a block beside it",
         "server as=player fill ~2 ~ ~ ~2 ~1 ~ minecraft:stone; server as=player setblock ~2 ~2 ~-1 minecraft:stone; "
         "wait 5t; ?place cobblestone ~2,2,0; wait 5t; block ~2,2,0 side=server; "
         "server as=player fill ~2 ~ ~-1 ~2 ~2 ~ minecraft:air", [r"^ok cobblestone$"]),
        # The ground in sight comes first, so a torch still stands on it with a wall beside it.
        ("place stands a torch on the ground before a wall",
         "server give @p minecraft:torch; server as=player setblock ~3 ~ ~ minecraft:stone; wait 5t; "
         "place torch ~2,0,0; wait 5t; block ~2,0,0 side=server; server as=player fill ~2 ~ ~ ~3 ~ ~ minecraft:air",
         [r"^ok torch$"]),
        # find expects no cobblestone near, so the steps before this one clear what they placed.
        ("place with no neighbour in sight fails at the first",
         "server as=player setblock ~2 ~2 ~ minecraft:stone; server as=player setblock ~4 ~2 ~ minecraft:stone; "
         "wait 5t; ?place cobblestone ~3,2,0; find cobblestone 6; server as=player fill ~2 ~2 ~ ~4 ~2 ~ minecraft:air",
         [r"err out_of_reach cannot see the face to place against; the crosshair hits stone ~2,2,0 on its west face",
          r"^ok none"], True),
        ("find by state", "server as=player setblock ~1 ~ ~-2 minecraft:oak_log[axis=y]; "
                          "server as=player setblock ~2 ~ ~-2 minecraft:oak_log[axis=x]; find oak_log[axis=x] 4 1; "
                          "server as=player fill ~1 ~ ~-2 ~2 ~ ~-2 minecraft:air", [r"^  oak_log ~2,0,-2$"]),
        ("block state errors", "?find oak_log[axis=up]; ?find stone[axis=x]",
         [r"err syntax axis of oak_log takes x\|y\|z, not up", r"err syntax stone has no states"], True),
        ("inventory moves", "offhand shield; equip iron_helmet; inv", [r"head:iron_helmet", r"offhand:shield"]),
        ("container transfer", "server as=player setblock ~3 ~ ~ minecraft:chest; open ~3,0,0; put cobblestone 5; "
                               "take cobblestone 2; close", [r"moved 5", r"moved 2"]),
        ("server-side nbt", f"nbt ~3,0,0 path=Items[0].{COUNT}", [r"ok 3"]),
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
        ("mouse left breaks the block aimed at",
         f"{CLEAR}; {AIMED}; server as=player setblock ~ ~ ~2 minecraft:stone; "
         "server item replace entity @p hotbar.7 with minecraft:air; select 7; wait 3t; mouse click left; wait 5t; "
         "block ~0,0,2", [r"^ok air$"]),
        ("mouse right places the held block",
         f"{CLEAR}; {AIMED}; server as=player setblock ~ ~ ~2 minecraft:stone; "
         "server item replace entity @p hotbar.7 with minecraft:cobblestone 16; select 7; wait 3t; "
         "mouse click right; wait 5t; block ~0,0,1", [r"^ok cobblestone$"]),
        ("mouse middle picks the block aimed at",
         f"{CLEAR}; {AIMED}; server as=player setblock ~ ~ ~2 minecraft:gold_block; "
         "server item replace entity @p hotbar.8 with minecraft:air; select 8; wait 3t; mouse click middle; wait 5t; "
         "inv full", [r"\*8:gold_block"]),
        ("click s<i> right takes half a stack",
         f"{CHEST}; click s0 right; wait 3t; click s1; wait 3t; nbt ~3,0,0 path=Items[1].{COUNT}; close",
         [r"^ok 8b?$"]),
        ("typing and select-all", f"key t; wait 3t; key {SELECT_ALL}; key backspace; type hello; wait 2t; screen full; "
                                  f"key {SELECT_ALL}; wait 2t; type x; wait 2t; screen full; key {SELECT_ALL}; "
                                  "key backspace; close", [r'value="hello"', r'value="x"']),
        ("scroll changes the selected slot",
         "server item replace entity @p hotbar.0 with minecraft:stone; "
         "server item replace entity @p hotbar.1 with minecraft:dirt; select 0; wait 2t; status; scroll -1; wait 3t; "
         "status", [r"hand:stone", r"hand:dirt"]),
        # Shift and the left arrow select the o, which the x replaces: hellxo without Shift, hellox without the arrow.
        ("a chord presses its last key with the others held",
         "key t; wait 3t; type hello; wait 2t; key left_shift+left; wait 2t; type x; wait 2t; screen full; "
         f"key {SELECT_ALL}; key backspace; close", [r'value="hellx"']),
        ("the agent control toggle is F8", "?key press f8", [r"^err syntax the agent control toggle"], True),
        ("fly", "fly on; wait 5t; fly off", [r"ok flying", r"ok not flying"]),
        ("screenshot", "screenshot 256", [r"^ok .+\.png t\d+$"]),
        ("screenshot burst", "screenshot burst=2 every=5t 256", [r"frames=2"]),
        ("record", f"record 1s {world} width=256 fps=10 audio=off",
         [rf"^ok .+{world}\.mp4 [\d.]+s 256x\d+ 10fps frames=(9|10|11) .*audio=off"]),
        ("record with sound", f"record 1s {world}-sound width=256 fps=10",
         [rf"^ok .+{world}-sound\.mp4 [\d.]+s 256x\d+ 10fps frames=(9|10|11) .*audio=on"]),
        ("camera move errors", "?camera move ~1,0,0 over 40t ease=fast; ?camera move ~1,0,0 "
                               + "via ~1,1,1 " * 33 + "over 40t",
         [r"^err syntax .*ease= takes linear\|in\|out\|inout", r"^err syntax .*at most 32 via groups"], True),
        ("record mark without a recording", "record mark x", [r"^err no_capture no recording is running"], True),
        ("record mark before the first frame", f"record start {world}-first width=256 audio=off; record mark first; "
                                               "record stop", [r"^ok mark first 0s frame=0$", r"^  first 0s frame=0$"]),
        ("settle", "settle", [r"settled="]),
        ("camera", "camera at ~4,3,4 look ~0,1,0; status; camera reset", [r"ok camera ", r"cam:"]),
        ("sound at a placed camera", "camera at ~40,2,0; sound minecraft:block.bell.use at ~40,0,3; "
                                     "sounds playing match=block.bell.use; camera reset",
         [r"^ok minecraft:block\.bell\.use master at -?[\d.]+,-?[\d.]+,-?[\d.]+ vol1 pitch1 t\d+$",
          r"block\.bell\.use master ~40(\.\d+)?,0(\.\d+)?,3(\.\d+)? vol\S+ pitch\S+ asked:1/1"]),
        ("interface sound", "sound minecraft:ui.button.click; sounds match=ui.button.click limit=1",
         [r"^ok minecraft:ui\.button\.click master ui vol1 pitch1 t\d+$", r"ui\.button\.click master ui"]),
        ("muted sound", "option soundCategory_block 0; sound minecraft:block.anvil.land at ~0,0,3 source=block; "
                        "sounds match=block.anvil.land limit=1; sounds playing match=block.anvil.land; "
                        "option soundCategory_block 1",
         [r"^ok minecraft:block\.anvil\.land block at .* t\d+ muted$", r"block\.anvil\.land block ~",
          r"^ok tick=\d+ count=0$"]),
        ("unknown sound", "sound minecraft:block.bell.uze",
         [r"^err syntax unknown sound minecraft:block\.bell\.uze; closest: .*minecraft:block\.bell\.use"], True),
        ("sound pitch out of range", "sound minecraft:block.bell.use pitch=3",
         [r"^err syntax pitch= takes 0\.5 to 2"], True),
        ("unknown sound in match", "sounds match=minecraft:block.bell.uze",
         [r"^err syntax unknown sound minecraft:block\.bell\.uze; closest: .*minecraft:block\.bell\.use.*; "
          r"or match=\*block\.bell\.uze\*$"], True),
        ("logs", "server title @p actionbar {\"text\":\"smoke\"}; wait 3t; messages kind=actionbar; sounds limit=1; "
                 "particles; log mark", [r"actionbar t\d+ smoke", r"mark=\d+"]),
        ("titles and subtitles", "server title @p subtitle {\"text\":\"smoke-sub\"}; "
                                 "server title @p title {\"text\":\"smoke-title\"}; wait 3t; messages kind=title; "
                                 "messages kind=subtitle", [r"title t\d+ smoke-title", r"subtitle t\d+ smoke-sub"]),
        ("tick control", "tick freeze; tick step 10; tick unfreeze", [r"frozen=true", r"frozen=false"]),
        ("wait until", "wait block ~0,-1,0 until id==grass_block timeout=2s", [r"ok \d"]),
        ("sample", "sample status every 2t x3", [r"samples=3"]),
        ("perf", "perf start smoke; wait 1s; perf stop", [r"smoke \d", r"mspt="]),
        ("perf for a duration", "perf 1s timed", [r"timed \d", r"mspt="]),
        ("perf times renderers and counts rebuilds",
         f"{CLEAR}; {AIMED}; server as=player setblock ~ ~ ~3 minecraft:chest; "
         "server as=player summon minecraft:cow ~2 ~ ~4; perf start effects; wait 5t; "
         "server as=player particle minecraft:flame ~ ~1 ~2 0.5 0.5 0.5 0 200; "
         "server as=player setblock ~1 ~ ~3 minecraft:stone; wait 1s; perf stop; server kill @e[type=minecraft:cow]",
         [r"rebuilds/s=(0\.[1-9]|[1-9])", r"^  renderer chest ", r"^  entity renderer cow ",
          r"^  particle render flame ", r"^  particle tick flame "]),
        ("snapshot", "snapshot save s1; snapshot list; snapshot restore s1", [r"saved s1", r"restored s1"]),
    ] + version_steps(game_version(args.game_dir))
    if args.baritone:
        # goto and mine walk through Baritone here, which the other steps don't expect.
        steps = [step for step in steps if step[0] == "create a test world"]
    failures = 0
    skipped = 0
    for name, script, expectations, *expected_error in steps:
        code, output = run(args.game_dir, script, as_json=args.json)
        reason = unsupported_reason(output, args.json) if name in MAY_BE_UNSUPPORTED else None
        if reason:
            skipped += 1
            print(f"skip {name}: {reason}")
            continue
        if args.json:
            problem = json_problem(script, output, expected_error)
        else:
            # A pattern with a leading ! must not appear.
            missing = [pattern for pattern in expectations
                       if bool(re.search(pattern.removeprefix("!"), output, re.MULTILINE)) == pattern.startswith("!")]
            # A step that checks an error reply exits non-zero, as every reply with an err line does.
            problem = f"exit {code}, missing {missing}" if (code != 0 and not expected_error) or missing else None
        if problem:
            failures += 1
            print(f"FAIL {name}: {problem}\n    " + output.strip().replace("\n", "\n    "))
            # A step's first error ends its script, before any close of its own, and would fail the steps after it too.
            run(args.game_dir, "?close")
        else:
            print(f"pass {name}")
    baritone = ("goto and mine go through Baritone when it is installed",
                lambda: check_baritone(args.game_dir, args.baritone))
    checks = [baritone] if args.baritone else [
        ("perception is passive", lambda: check_perception_is_passive(args.game_dir)),
        ("grammar file", lambda: check_grammar_file(args.game_dir)),
        ("mine by type moves on from a block that changed", lambda: check_mine_moves_on(args.game_dir)),
        ("server function answers as the game sees it", lambda: check_functions(args.game_dir, world)),
        ("gametest run reports each test", lambda: check_gametests(args.game_dir, world)),
        ("the logs start anew in each world", lambda: check_logs_per_world(args.game_dir, world)),
        ("wait 1t lets one tick pass", lambda: check_tick_waits(args.game_dir)),
        ("server steps run back to back", lambda: check_server_steps(args.game_dir)),
        ("mouse left in a screen presses a button", lambda: check_buttons_in_screen(args.game_dir)),
        ("the cursor stays when a screen opens another", lambda: check_cursor_stays_across_screens(args.game_dir)),
        ("mouse on slots: right and shift clicks", lambda: check_mouse_on_slots(args.game_dir)),
        ("mouse move turns the camera", lambda: check_camera_turns(args.game_dir)),
        ("mouse move moves the cursor in a screen", lambda: check_cursor_moves(args.game_dir)),
        ("a server step right after the game menu closes runs", lambda: check_server_after_pause(args.game_dir)),
        ("frames are upright", lambda: check_frame_upright(args.game_dir)),
        ("pixels finds a world position", lambda: check_world_position_in_frame(args.game_dir)),
        ("hud off hides the hud", lambda: check_hud_hides(args.game_dir)),
        ("perf counts live particles", lambda: check_live_particles(args.game_dir)),
        ("perf times the render stages", lambda: check_render_stages(args.game_dir)),
        ("stop leaves what was timed running", lambda: check_stop_leaves_timed_running(args.game_dir, world)),
        ("videos are upright", lambda: check_video_upright(args.game_dir, world)),
        ("a mark names the frame its change shows from", lambda: check_marks(args.game_dir, world)),
        ("a camera move takes its time and ends at its point", lambda: check_camera_move(args.game_dir)),
        ("a camera move's easing", lambda: check_camera_easing(args.game_dir)),
        ("a curved camera move has no corner and an even speed", lambda: check_camera_curve(args.game_dir)),
        ("a camera move looking ahead faces its way", lambda: check_camera_looks_ahead(args.game_dir)),
        ("a camera away loads and plays the world around it", lambda: check_camera_away(args.game_dir)),
        ("a camera move in the background", lambda: check_camera_move_background(args.game_dir)),
        ("a recorded camera move is in a new place each frame",
         lambda: check_camera_move_recording(args.game_dir, world)),
        ("@<name> takes an entity by its tag or name", lambda: check_entity_names(args.game_dir)),
        ("a camera follows an entity", lambda: check_camera_follow(args.game_dir)),
        ("a camera orbits a point or an entity", lambda: check_camera_orbit(args.game_dir)),
        ("a placed camera's field of view and roll", lambda: check_camera_lens(args.game_dir)),
        ("look and find around a camera placed away",
         lambda: check_surroundings_of_an_away_camera(args.game_dir)),
        ("slow motion by the tick rate", lambda: check_slow_motion(args.game_dir, world)),
        ("a recording ends with its script", lambda: check_record_script(args.game_dir, world)),
        baritone,
    ]
    for name, check in checks:
        try:
            problem = check()
        except Skipped as reason:
            skipped += 1
            print(f"skip {name}: {reason}")
            continue
        except (AssertionError, ValueError, IndexError, KeyError, subprocess.CalledProcessError) as error:
            problem = repr(error)
        if problem:
            failures += 1
            print(f"FAIL {name}: {problem}")
            # So that one failure, which may leave a screen open, doesn't fail the checks after it too.
            run(args.game_dir, "?close")
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
    if args.shapes:
        failures += 1 if write_shapes(args.game_dir, args.shapes) else 0
    code, output = run(args.game_dir, f"leave; delete world {world}")
    for recording in glob.glob(os.path.join(args.game_dir, "blockpilot", "recordings", f"{world}*.mp4")):
        os.remove(recording)
    if code == 0 and f"deleted {world}" in output:
        print("pass clean up")
    else:
        failures += 1
        print(f"FAIL clean up: {output.strip()}")
    total = len(steps) + len(checks) + 2 + (1 if args.shapes else 0) - skipped
    print(f"{total - failures}/{total} passed, {skipped} skipped")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())

#!/usr/bin/env python3
"""End-to-end smoke test against a running game with Blockpilot.

    python3 tools/smoke_test.py --game-dir <run dir> [--json] [--shapes <file>]

Creates a throwaway flat test world, exercises the command groups (perception, actions, inventory, containers,
crafting, raw input, observation, the integrated server, sampling, performance capture, snapshots, JSON output),
checks each reply and what input and capture do in the game, then leaves and deletes the world. Exit code 0 when every
step passed; a step that can't run here, such as one needing ffmpeg, is reported as skipped, with why.

--json runs the same steps in JSON mode and checks the structure of each reply instead of its text: one JSON line
with one result per step, each ok unless the step expects an error.

--shapes writes the JSON structure (field names, row field names) of a set of queries to a file, so runs on two
loaders can be compared with diff.

Every step goes through the launcher the game writes into <run dir>/blockpilot at start.
tools/test_targets.py runs this on every target.
"""
import argparse
import glob
import json
import os
import re
import shutil
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
# The game's select-all shortcut: Command on macOS, Control elsewhere.
SELECT_ALL = "left_win+a" if sys.platform == "darwin" else "left_control+a"
# Facing south from a block's centre at a known place: a block at ~0,0,2 is under the crosshair at pitch 40.
AIMED = "server as=player tp @s 0.5 -60 0.5 0 40; wait 3t"
LEVEL = "server as=player tp @s 0.5 -60 0.5 0 0; wait 5t"
# Steps a game may not be able to run: recording needs ffmpeg, and sound the game's audio capture.
MAY_BE_UNSUPPORTED = {"record", "record with sound"}
# Leaves that place empty; the stone is there so that the fill always changes something, which it must not to fail.
CLEAR = "server setblock 6 -57 6 minecraft:stone; server fill -3 -60 -3 6 -57 6 minecraft:air"
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
    missing = [row["ebnf"] for row in json.loads(output.splitlines()[0])["results"][0]["rows"]
               if row["ebnf"] not in lines]
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


def version_steps(version):
    """The steps whose commands or answers depend on the game's version."""
    if version >= (26, 1):
        graphics = "option graphicsPreset fast; option reset", [r"graphicsPreset fast was", r"restored graphicsPreset"]
    else:
        graphics = "option graphicsMode fast; option reset", [r"graphicsMode fast was", r"restored graphicsMode"]
    # Key names are the running game's, and 26.3's key table has no F25.
    if version >= (26, 3):
        function_key = "?key press f25", [r"^err syntax unknown key f25; closest: "], True
    else:
        function_key = "key press f25", [r"^ok"]
    # The game polls for this key as it handles the drop key: Command on macOS before 26.1, else Control.
    drop_all = "left_win" if sys.platform == "darwin" and version < (26, 1) else "left_control"
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
    _, output = run(game_dir, f"click {x},{y} right; wait 3t; click s1; wait 3t; nbt ~3,0,0 path=Items[1].count")
    if not re.search(r"^ok 8$", output, re.MULTILINE):
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


def write_shapes(game_dir, path):
    """Writes one line per query; returns how many replies could not be read."""
    lines = []
    unreadable = 0
    # `look`'s rows have the shape of what is near, which would otherwise be whatever earlier steps happened to drop.
    run(game_dir, "server as=player summon minecraft:item ~2 ~ ~ "
                  "{PickupDelay:32767,Item:{id:\"minecraft:stone\",count:1}}; wait 2t")
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
        ("place against a face out of sight", "server as=player setblock ~2 ~2 ~ minecraft:stone; wait 5t; "
                                              "?place cobblestone ~3,2,0; find cobblestone 6; "
                                              "server as=player fill ~1 ~2 ~ ~2 ~2 ~ minecraft:air",
         [r"err out_of_reach cannot see the face to place against; the crosshair hits stone ~2,2,0 on its west face",
          r"^ok none"], True),
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
         f"{CHEST}; click s0 right; wait 3t; click s1; wait 3t; nbt ~3,0,0 path=Items[1].count; close", [r"^ok 8$"]),
        ("typing and select-all", f"key t; wait 3t; key {SELECT_ALL}; key backspace; type hello; wait 2t; screen full; "
                                  f"key {SELECT_ALL}; wait 2t; type x; wait 2t; screen full; key {SELECT_ALL}; "
                                  "key backspace; close", [r'value="hello"', r'value="x"']),
        ("scroll changes the selected slot",
         "server item replace entity @p hotbar.0 with minecraft:stone; "
         "server item replace entity @p hotbar.1 with minecraft:dirt; select 0; wait 2t; status; scroll -1; wait 3t; "
         "status", [r"hand:stone", r"hand:dirt"]),
        # Without Shift held, the left arrow only moves the cursor, and backspace then takes the second l.
        ("a chord presses its last key with the others held",
         "key t; wait 3t; type hello; wait 2t; key left_shift+left; wait 2t; key backspace; wait 2t; screen full; "
         f"key {SELECT_ALL}; key backspace; close", [r'value="hell"']),
        ("the agent control toggle is F8", "?key press f8", [r"^err syntax the agent control toggle"], True),
        ("fly", "fly on; wait 5t; fly off", [r"ok flying", r"ok not flying"]),
        ("screenshot", "screenshot 256", [r"^ok .+\.png t\d+$"]),
        ("screenshot burst", "screenshot burst=2 every=5t 256", [r"frames=2"]),
        ("record", f"record 1s {world} width=256 fps=10 audio=off",
         [rf"^ok .+{world}\.mp4 [\d.]+s 256x\d+ 10fps frames=(9|10|11) .*audio=off"]),
        ("record with sound", f"record 1s {world}-sound width=256 fps=10",
         [rf"^ok .+{world}-sound\.mp4 [\d.]+s 256x\d+ 10fps frames=(9|10|11) .*audio=on"]),
        ("camera", "camera at ~4,3,4 look ~0,1,0; status; camera reset", [r"ok camera ", r"cam:"]),
        ("logs", "server title @p actionbar {\"text\":\"smoke\"}; wait 3t; messages kind=actionbar; sounds limit=1; "
                 "particles; log mark", [r"actionbar t\d+ smoke", r"mark=\d+"]),
        ("titles and subtitles", "server title @p subtitle {\"text\":\"smoke-sub\"}; "
                                 "server title @p title {\"text\":\"smoke-title\"}; wait 3t; messages kind=title; "
                                 "messages kind=subtitle", [r"title t\d+ smoke-title", r"subtitle t\d+ smoke-sub"]),
        ("tick control", "tick freeze; tick step 10; tick unfreeze", [r"frozen=true", r"frozen=false"]),
        ("wait until", "wait block ~0,-1,0 until id==grass_block timeout=2s", [r"ok \d"]),
        ("sample", "sample status every 2t x3", [r"samples=3"]),
        ("perf", "perf start smoke; wait 1s; perf stop", [r"smoke \d", r"mspt="]),
        ("perf times renderers and counts rebuilds",
         f"{CLEAR}; {AIMED}; server as=player setblock ~ ~ ~3 minecraft:chest; "
         "server as=player summon minecraft:cow ~2 ~ ~4; perf start effects; wait 5t; "
         "server as=player particle minecraft:flame ~ ~1 ~2 0.5 0.5 0.5 0 200; "
         "server as=player setblock ~1 ~ ~3 minecraft:stone; wait 1s; perf stop; server kill @e[type=minecraft:cow]",
         [r"rebuilds/s=(0\.[1-9]|[1-9])", r"^  renderer chest ", r"^  entity renderer cow ",
          r"^  particle render flame ", r"^  particle tick flame "]),
        ("snapshot", "snapshot save s1; snapshot list; snapshot restore s1", [r"saved s1", r"restored s1"]),
    ] + version_steps(game_version(args.game_dir))
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
        else:
            print(f"pass {name}")
    checks = [
        ("perception is passive", lambda: check_perception_is_passive(args.game_dir)),
        ("grammar file", lambda: check_grammar_file(args.game_dir)),
        ("mouse left in a screen presses a button", lambda: check_buttons_in_screen(args.game_dir)),
        ("the cursor stays when a screen opens another", lambda: check_cursor_stays_across_screens(args.game_dir)),
        ("mouse on slots: right and shift clicks", lambda: check_mouse_on_slots(args.game_dir)),
        ("mouse move turns the camera", lambda: check_camera_turns(args.game_dir)),
        ("mouse move moves the cursor in a screen", lambda: check_cursor_moves(args.game_dir)),
        ("frames are upright", lambda: check_frame_upright(args.game_dir)),
        ("pixels finds a world position", lambda: check_world_position_in_frame(args.game_dir)),
        ("hud off hides the hud", lambda: check_hud_hides(args.game_dir)),
        ("perf counts live particles", lambda: check_live_particles(args.game_dir)),
        ("videos are upright", lambda: check_video_upright(args.game_dir, world)),
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

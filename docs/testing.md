# Testing a mod with Blockpilot

Recipes for developing and testing a mod with Blockpilot driving the game: smoke tests a CI job can run, checks of what
the player sees and hears, and how they fit with Minecraft's own GameTests. Each recipe is a script for the launcher
(`run/blockpilot/blockpilot` in a mod's dev environment, set up as in [dev-environment.md](dev-environment.md)). They
use vanilla blocks, so they run as they are; put your mod's blocks, items and sound ids in their place. Every step
answers `ok` or `err`, the first `err` ends the script, and the launcher exits 0 only when every step answered `ok`, so
a script is a test as it stands.

## Blockpilot and Minecraft's GameTests

Minecraft has its own test framework: GameTests, run with `/test`, registered by mods (NeoForge's `@GameTestHolder`,
Fabric's `fabric-gametest` entrypoint) and, on 26.x, also written as data. A GameTest builds a structure on the
server, runs code or redstone in it and passes or fails. That makes it fast, repeatable and runnable on a headless
server in CI, the right tool for what the server decides: block entities, redstone, mobs, recipes, loot.

A GameTest never sees the client: not a screen or its buttons, the HUD, key bindings, what a frame shows, a sound as the
player hears it, a chat line as it is shown, the client's copy of a block, or frame times. Blockpilot plays a real
client as a player does, with its mouse, keys, screens and camera, scripted from outside: a shell script, a CI job or
an AI agent over MCP, with no test code in your mod, on NeoForge, Fabric and plain Minecraft alike. It reads the
integrated server too (`server`, `nbt`, `side=server`), so one script checks both sides. The price: it needs a client
with a window, and a real client renders and ticks on its own time, so its tests are slower and less exact than a
GameTest.

Fabric also has client game tests (Fabric API's `fabric-client-gametest-api-v1`, in the 26.x Fabric API Blockpilot
builds against, not the 1.21.1 one): Java tests that drive a client from inside the game, run by Gradle. They are the
closest built-in tool. Blockpilot differs in being driven from outside, in its own command language rather than Java,
the same on every loader, and in staying live while you or an agent look around.

So use GameTests for the server's logic and Blockpilot for what the player sees, hears and does, from end to end. They
meet in `gametest run`, which runs your GameTests from a Blockpilot script (the recipe below), so one script and one
exit code cover both.

## A smoke test in a fresh world

```sh
B=run/blockpilot/blockpilot
$B --wait-ready 600 "create world smoke flat; server setblock ~2 ~ ~ minecraft:redstone_lamp; \
  server setblock ~2 ~1 ~ minecraft:lever[face=floor]; use ~2,1,0; wait 4t; assert block ~2,0,0 props.lit==true"
$B "leave; delete world smoke"
```

`create world` makes a throwaway world with cheats on and joins it; `delete world` takes only worlds it made. `use`
flips the lever with a real right click, aimed as a player would, so the check covers your block's interaction, not
just its state. `assert` fails the script with `err assert_failed` and what it saw; a block's states are its `props`,
as `--json` shows a reply's fields.

## Starting the game in CI

```sh
./gradlew runClient &
run/blockpilot/blockpilot --wait-ready 600 --wait-world "status"
./smoke.sh
run/blockpilot/blockpilot "quit"
```

`--wait-ready` waits for the game to start, and `--wait-world` also for a world when the run joins one on its own
(`blockpilot.autojoin` in [dev-environment.md](dev-environment.md)). A crash ends the wait with exit code 2 and the
crash report's path. `quit` returns once the game has exited. The game needs a window: on a Linux runner without a
display, start it under a virtual one, such as `xvfb-run -a ./gradlew runClient`.

## A screen and its buttons

```sh
$B "server setblock ~2 ~ ~ minecraft:crafting_table; use ~2,0,0; screen; assert screen screen==crafting; close"
```

`screen` lists the open screen's widgets and slots, numbered `w<i>` and `s<i>`, which `click w3` or `click s0` press.
Its reply starts with the screen's type and title, `ok crafting "Crafting" …`, which `assert screen` checks as `screen=`
and `title=`, so a test sees your screen open when the player opens it. `type` and `key` work in it too.

## A sound or a message

```sh
$B "server setblock ~2 ~ ~ minecraft:lever[face=floor]; use ~2,0,0; \
  wait sounds since=20t match=block.lever.click until count>0 timeout=2s"
$B "server say engaged; wait messages since=2s match=*engaged* until count>0 timeout=5s"
```

The sound log holds every sound the client started, with where it played from the player, its volume and pitch; the
message log holds chat, the action bar and titles. `since=` takes a duration back from now, or the `tick` of an earlier
reply.

## Both sides of a block

```sh
$B "server setblock ~2 ~ ~ minecraft:redstone_lamp; wait 2t; block ~2,0,0; block ~2,0,0 side=server"
```

`side=server` reads the integrated server's copy, so a test catches a block whose state never reached the client. `nbt
~2,0,0` gives a block entity's server-side data, and `sample entity e5 and entity e5 side=server every 1t x20` an
entity's drift between the two.

## Your GameTests in the same script

```sh
$B --json "gametest run mymod:*" | jq -e '.results[0].failed == 0'
```

`gametest run` runs the GameTests whose ids match: `namespace:path` on 26.x (`minecraft:always_pass` is one to try),
`class.method` in lower case on 1.21.1. It answers with `passed=` and `failed=` and a row per test, the failing ones
with their message, and answers `ok` even when some failed, so the check above turns a failure into an exit code. On
NeoForge, GameTests run only in a dev run started with `-Dneoforge.enableGameTest=true`, set next to the autojoin lines
of [dev-environment.md](dev-environment.md):

```kotlin
neoForge {
    runs {
        named("client") {
            systemProperty("neoforge.enableGameTest", "true")
        }
    }
}
```

A data pack's tests load with the world: after adding one, `leave` and `join world` again. `server test clearall`
removes the structures the tests built.

## The same state for every test

```sh
$B "create world bench flat; server setblock ~2 ~ ~ minecraft:redstone_lamp; \
  server setblock ~2 ~1 ~ minecraft:lever[face=floor]; snapshot save base"
$B "snapshot restore base; use ~2,1,0; wait 4t; assert block ~2,0,0 props.lit==true"
```

`snapshot save` copies the whole save folder, and `snapshot restore` puts it back and joins again, so each test starts
from the same blocks, inventory and time. A mod with static state across worlds may still keep it; relaunch the client
when a test needs a clean start.

## A performance budget

```sh
$B --json "perf 10s farm" | jq -e '.results[0].summary.server.mspt.p99 < 50'
```

`perf` captures MSPT, FPS, the busiest block entities, entities, renderers and particles, network traffic and, with
`profile`, the server thread's stacks by mod, and keeps the capture in `blockpilot/perf/<name>.json`. `load server 30`
adds 30 ms to every server tick, and `load net 150` holds every packet for 150 ms, to see your mod under a slow server
or a laggy connection.

## What the frame shows

```sh
$B "server setblock ~0 ~1 ~3 minecraft:glowstone; face ~0,1,3; wait 5t; pixels @~0,1,3; screenshot"
```

`pixels @<pos>` gives the mean colour and brightness of where a block is drawn in the last frame, so a test can check a
light, a colour or a model without comparing images; `screenshot` keeps the frame as a PNG, and `screenshot burst=8
every=5t sheet` a sequence in one image.

## A video of the problem

```sh
$B "server setblock ~2 ~ ~ minecraft:redstone_lamp; server setblock ~2 ~1 ~ minecraft:lever[face=floor]; \
  record script repro; use ~2,1,0; record mark flipped; wait 2s"
```

`record script` records until its script ends, however it ends, so a failing step still leaves a video, with the marks
in `repro.marks.json`. It needs ffmpeg; [recording.md](recording.md) has the camera moves for a demo.

## Every version and loader

```sh
for dir in ../mymod-1.21.1/run ../mymod-26.3/run; do
  "$dir/blockpilot/blockpilot" "status" || exit 1
done
```

The commands are the same on every target, NeoForge, Fabric and plain Minecraft from 1.21.1 to 26.3, so one script
tests them all; `--game-dir <dir>` points a launcher at another game. Key names are the running game's.

## An agent as a tester

With the launcher added over MCP ([connecting.md](connecting.md)), an AI agent plays the game through the same
commands: ask it to place your machine, feed it and check what comes out, and it explores, screenshots and reports, and
can write what it found down as a script for the recipes above. With [Baritone](https://github.com/cabaletta/baritone)
installed, its `goto` and `mine` walk through Baritone.

[Back to the README](../README.md)

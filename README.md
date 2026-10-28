# Blockpilot

A Minecraft mod that lets AI agents and scripts observe and control the game through a fast, token-efficient local
interface. It runs in the client, for NeoForge and Fabric on Minecraft 1.21.1 and 26.1 (26.1.2), built from one
source tree with [Stonecutter](https://stonecutter.kikugie.dev/) the same way as Ultrasplosive. Agents play the game, or
play-test a mod under development: real input (clicks, holds, drags, keys), screenshots, structured results, condition
waits, the integrated server's commands with captured output, sound/message logs and performance captures.

## Using it

Put the jar for your loader into the client's `mods` folder (or a dev environment's `run/mods`) and start the game.
Once it is up, `<game dir>/blockpilot/endpoint.json` names the port (25599, or the next free one when several
instances run) and the token file. The endpoint listens on `127.0.0.1` only and needs the token.

**From a shell**: at every start the game writes a launcher, `<game dir>/blockpilot/blockpilot`
(`blockpilot.cmd` on Windows), that runs Blockpilot's command-line client on the game's own Java with the jar it
loaded, so it needs no `java` on the path, no Python and no Blockpilot checkout. It stays after the game exits and
works across restarts; it appears while the game loads, so only the very first start in a directory has to wait
for the file before using it.

```sh
run/blockpilot/blockpilot --wait-ready 300 --wait-world "status; look"
run/blockpilot/blockpilot --json "inv"
```

`--wait-ready <seconds>` waits for the game to come up, and `--wait-world` also for the player to be in a world.
A crash report the game writes meanwhile ends the wait at once, with exit code 2 and the report's path. A client
that can't open its window fails before any mod loads, so nothing reports that and the wait runs to its timeout; on
macOS that is a client started while the display sleeps ("Failed to locate a primary monitor"), which
`caffeinate -u -t 300` before the start avoids by waking the display. `--game-dir <dir>` talks to the game in another
directory, and `--help` lists the options.
Exit codes: 0 every command returned `ok`, 1 one returned `err`, 2 the game could not be reached (or the wait timed out,
or no reply came within `--timeout`, default 300, which cancels the script; stderr says after how many seconds). When the
game's client thread finishes no tick for 30 s outside Blockpilot's own commands, for example flooded with chat by a
command block, every waiting script answers `err stalled` with how long and how many chat lines arrived. When a
step failed or was skipped, one line on stderr counts them, names the failed steps (from 1) and says whether anything
ran, e.g. `blockpilot: 1 err (step 3), 2 skip (nothing after the error ran)`. After a `quit` the CLI returns only once
the game process has exited (within `--timeout`, else exit code 2), so a restart can follow straight away.

**Over MCP** (three tools: `run`, `screenshot`, `help`): the launcher also serves MCP over stdio. The MCP client
starts it once per session, so the tools are there before the game is and keep working through every restart of
the game, with no reconnect. Start the game once in that directory first, so the launcher exists, then from the
directory that holds `run`:

```sh
claude mcp add --transport stdio blockpilot -- "$PWD/run/blockpilot/blockpilot" --mcp
```

On Windows the command is `cmd /c <game dir>\blockpilot\blockpilot.cmd --mcp`. While no game runs, a tool call
answers with an error naming the game directory, and the first call after the game is up works. A restarted game
gets a new session, so per-session settings such as `fmt json` start from their defaults again.

The game also serves MCP itself, over Streamable HTTP, for clients that can't launch a process. That server goes
away with each restart of the game, and Claude Code doesn't retry an HTTP server that was down when its session
started:

```sh
claude mcp add --transport http blockpilot http://127.0.0.1:25599/mcp --header "Authorization: Bearer $(cat run/config/blockpilot/token)"
```

The port is in `endpoint.json` (another game may already hold 25599). As a baseline, a Claude Code session that
created a survival world, mined four logs, collected the drops, crafted planks and a crafting table and placed it
needed six `run` calls and about 1,300 characters (roughly 500 tokens) of tool input and output, with no screenshots.

**Line protocol** (`nc 127.0.0.1 25599`): send `auth <token>`, then one script per line, optionally prefixed with
`#<id> `. Text replies end with a line holding only `.`; after `fmt json` each reply is one JSON line. `sub on` pushes
events as they happen.

The window can stay in the background: while an agent is connected, and for a minute after its last connection closed (the CLI opens one per call), the game does not pause and keeps handling input.
Real keyboard or mouse input to the focused window cancels running agent actions; F8 toggles agent control off and on.
A line in the top-left corner shows when an agent is active and what it is doing.

### In another mod's dev environment

`./gradlew publishToMavenLocal` publishes `dev.blockpilot:blockpilot-neoforge` and `blockpilot-fabric` (version
`<mod version>+<minecraft>`, e.g. `0.1.0-alpha.1+1.21.1` or `0.1.0-alpha.1+26.1.2`) and `dev.blockpilot:blockpilot-api`,
the same for every target. In the other project,
add `mavenLocal()` to the repositories and Blockpilot as a runtime-only dependency. `blockpilot.autojoin` joins that
world on launch, and `blockpilot.autocreate=flat|void|normal` creates it first if it is missing.

ModDevGradle (`localRuntime` as in the NeoForge MDK; `runtimeOnly` works too):

```kotlin
dependencies {
    localRuntime("dev.blockpilot:blockpilot-neoforge:0.1.0-alpha.1+1.21.1")
}
neoForge {
    runs {
        named("client") {
            systemProperty("blockpilot.autojoin", "mymod-test")
            systemProperty("blockpilot.autocreate", "flat")
        }
    }
}
```

Fabric Loom (for 26.1, whose Loom doesn't remap mods, `localRuntime` instead of `modLocalRuntime`):

```kotlin
dependencies {
    modLocalRuntime("dev.blockpilot:blockpilot-fabric:0.1.0-alpha.1+1.21.1")
}
loom {
    runs {
        named("client") {
            vmArg("-Dblockpilot.autojoin=mymod-test")
            vmArg("-Dblockpilot.autocreate=flat")
        }
    }
}
```

Then `run/blockpilot/blockpilot --wait-ready 300 --wait-world "status"`, run from that project, returns once the
player is in the test world (on the first start, once the game has written the launcher). In this repository the
same properties go through Gradle:
`./gradlew :blockpilot:1.21.1-neoforge:runClient -Pblockpilot.autojoin=test -Pblockpilot.autocreate=flat` (or
`26.1-neoforge`, `1.21.1-fabric`, `26.1-fabric`).

## The command language

A script is commands separated by `;`; each command answers with one result line (`ok …`, `err <code> …` or `skip`
after a failure) plus indented rows. Positions are `x,y,z` or `~dx,dy,dz` (relative to the player's block), entities
`e<id>`, counts `x<n>`, durations `20t` or `3s`, options `name=value`. Ids drop the `minecraft:` namespace. Repeating
`look`, `inv` or `screen` in a session reports only what changed (`full` forces everything). Game events (hurt, got,
chat, actionbar, screen, died, …) arrive as `! …` lines with the next reply. A script stops at the first `err`
unless the command is written `?cmd`, which reports its error and goes on (handy for probes). `assert <query>
<predicates>` turns a script into a test: it fails with `err assert_failed` and the observed values when the
predicates (as in `wait … until`) don't hold, and `if <query> <predicates> then <command>` runs the command only when
they do. A predicate's value can name another field of the same result, as in `hp<$max_hp`. Every tick number in a
reply (`tick query`, `sample` rows, waits, `sounds`, `messages` and their `since=`, which also takes a duration back
from now such as `since=60t`) is the same clock: the integrated server's game time in singleplayer, else the client's.
`help`, `help <command|group>` and `help errors` give the full reference.

The language has a grammar, written in EBNF in the `command-language` spec, and a recursive-descent parser that
follows it; `help grammar` prints it, with one production per command generated from that command's declaration. A
whole script is parsed before any of it runs, so a typo in its last step stops it before the first one acts: that
step answers `err syntax col <n>: <hint>`, naming what the command takes there, and every other step `skip`. Common
slips get a pointed hint, e.g. `id=air` in a predicate (predicates compare with `==`), `hp < 10` (a predicate is one
token), or `wait 5` (a duration needs its unit).

```
> select iron_pickaxe; mine #logs x3; inv
ok hand:iron_pickaxe
ok mine 3/3 oak_log
ok
  ~ 1:iron_pickaxe dur247/250
  + 5:oak_log x3
! got oak_log x3
```

| Group | Commands |
| --- | --- |
| perception | `status` (one line: position, health, crosshair target with the hit point `hit=u,v,w` in the block, velocity, vehicle, camera angles…), `look [r]`, `entity e<id> [side=server]` (one entity's position, yaw, pitch and velocity per tick; `side=server` the integrated server's copy, so `sample entity e5 and entity e5 side=server every 1t x20` shows the client's drift), `find <block\|#tag\|*pattern\|entity> [r [limit]]` (a cube, r up to 128; limit up to 100, with `count=` when it left some out; blocks aboard a sub-level: `sable:find`), `block <pos>`, `blocks <from> <to> [grid]` (`grid`: one palette symbol per cell instead of run lengths), `inv [detail]` |
| observe | `screenshot [width] [burst=n every=Nt [sheet]] [crop=x0,y0,x1,y1] [hud=off]` (answers each PNG's path with `t<tick>`, the tick it was captured on; width up to 1280 and the frame's or crop's own, with `width=` when narrower than asked; `sheet`: one PNG of the burst in a labelled grid; a frame rendered from a placed camera carries `camera <x,y,z>`), `pixels [[name=]x0,y0,x1,y1\|[name=]@<pos>]… [r=]` (mean rgb and luma, max and p90 luma of parts of the last frame, with `camera <x,y,z>` when a placed camera rendered it), `option [<name> [<value>]]`, `option reset`, `settle [timeout=]` (waits until the chunks around the player have arrived and those in view have compiled, e.g. after `camera at`, a teleport or a render distance change), `camera at <pos> [look <pos\|e<id>>]` (`unloaded` when the client hasn't loaded the camera's chunk; for an effect spanning hundreds of blocks, `option fov 110` and `option renderClouds off` widen and clear the view) / `camera reset\|off`, `sounds [since=] [match=] [limit=]` (starts, the last 2000; `vol` and `pitch` are what plays, and `asked:<vol>/<pitch>` the values it was played with where its sounds.json entry scales them; a `match=` without `*` is a sound event id, `minecraft:` without a namespace, and an unknown one fails naming the closest), `sounds playing [since=] [match=] [limit=]` (what plays now, with `loop` and `played=<s>`; a sound whose category or the master volume is 0 never plays), `messages [since=] [kind=] [match=]` (`match=*engaged*`: whole text, any case; `sounds` and `messages` add `from=<tick>`, where their log begins, when it dropped some of the asked window), `particles [since=]`, `log [level=] [since=] [grep=] [logger=] [limit=]`, `log mark`, `log detail <seq>` |
| movement | `face <pos\|e<id>> [at <u,v,w>]`, `turn <yaw> <pitch> [Nt]`, `move <dir> <blocks\|Nt>` (by blocks, `err stuck` after 20 ticks without progress), `jump`, `sneak on\|off`, `sprint on\|off`, `fly on\|off`, `goto <pos\|e<id>> [near=]` (to a position without `near=`, `err no_path` at once with the reason when the player can't stand there, e.g. `stone fills ~2,-1,0; stand at ~2,0,0`) |
| interact | `use [target [at <u,v,w>]] [any] [sneak] [hold=Nt]` (clicks a block or entity only once the crosshair rests on it, else `err obstructed <what>` or `err out_of_reach`; `any` clicks whatever the crosshair ends on, adding `off_target=<target>` when that was something else; `sneak` with an item in either hand uses the item instead of the block, and the reply names it as `sneak_item`; `at`: a point inside the block, each 0 to 1 in the frame of its model facing north, turned with the block, as `status` shows it in `hit=`; the server gets the crosshair hit, where the aimed ray first meets a block's shape, which the reply reports as `aim` and `hit=`), `attack [e<id>\|type] [x<n>]`, `mine [pos\|block\|#tag] [x<n>]` (`err unbreakable <block>` at once for a block the player can't break; by type such blocks are passed over), `place <item> <pos>` |
| inventory | `select <slot\|item>`, `swap <a> <b>`, `drop <item\|slot> [n]`, `offhand <item>`, `equip <item>`, `open <pos>`, `take <item\|s<i>> [n]`, `put <item\|s<i>> [n]`, `craft <item> [x<n>]` (through the recipe book, in the 2x2 grid or at a table within 4 blocks; from 26.1 a client on a remote server knows only unlocked recipes, so there `craft` finds only those) |
| gui | `screen` (lists rows of lists as `w<i>` too), `cursor <x,y\|w<i>>`, `click <x,y\|w<i>\|s<slot>> [button]` (`w<i>`: a widget's centre, as `screen` numbers them), `close` |
| input | `mouse down\|up\|click <button>`, `mouse move <dx> <dy> [Nt]`, `drag <button> …`, `scroll <n>`, `key down\|up\|press <key\|key.mapping>` (a key alone is pressed: `key escape`), `type <text>`, `release` |
| talk | `chat <text>`, `cmd <command>` |
| session | `worlds`, `servers`, `join world\|server <name>`, `leave`, `quit` (leaves, answers `ok pid=<pid>` and closes the game), `respawn`, `create world <name> [flat\|void\|normal] …`, `snapshot save\|restore\|delete <name>`, `snapshot list`, `delete world <folder>` (test worlds only), `config` (the mods with NeoForge config files), `config <mod> [client\|common\|server\|startup] [match=]` (their values by dotted key), `config <mod> [<type>] <key> [<value>]` (a value's default, what it takes and the mod's comment; with a value, sets it in memory, never in the file, and tells the mod as a reload of its file does; `err needs_restart` for a value only a game restart, or for a server config a world load, would apply; server configs need a world, and setting one the integrated server), `config reset` (puts back what agents changed, as leave, quitting, the agent-control key and shutdown do) |
| integrated | `server [as=player] [settle=Nt] <command>` (result value and feedback lines; runs at the player's position and dimension, so `~` and `@p` count from the player, and with `as=player` as the player; a failing command, a failing `execute if` test among them, is `err command_failed` with its `result`, which `?server …` lets the script carry on past), `nbt <pos\|e<id>\|uuid> [path=]`, `tick freeze\|unfreeze\|step <n>\|rate <tps>\|query`, `block`/`blocks`/`find … side=server` |
| performance | `perf start [name]`, `perf stop` (MSPT/TPS, FPS and 1% low, time per block-entity and entity type, per renderer, per particle type with live counts, per render stage on NeoForge, network; JSON summary saved to `blockpilot/perf/<name>.json`), `perf status`, `load server\|client\|net <ms> [jitter=<ms>]` (idle time added on purpose to every integrated-server tick, counted in its MSPT, or every frame, or a delay held on every packet of the client's connection each way, order kept, to reproduce a busy server, a slow machine or a laggy network without touching the world; `load` reports it, `load off` ends it, as do leaving the world and closing) |
| sable | with Sable only: `sable:list [r] [side=server]`, `sable:info <sub> [brief] [side=server]` (`brief`: yaw, pitch, roll, speed, climb, world y, height above ground), `sable:find <sub> <block\|#tag\|*pattern> [limit] [sort=x\|y\|z\|-x\|-y\|-z] [near=<pos>]`, `sable:world <pos>`, `sable:plot <sub> <pos>`, `sable:tp <pos>`, `sable:tp <sub> <block\|#tag\|*pattern>` (see [With Sable](#with-sable)) |
| meta | `help`, `help grammar`, `fmt text\|json`, `sub on\|off`, `sub log <level>\|off`, `stop`, `wait <Nt>`, `wait <query> until <field><op><value> [clock=server]`, `assert <query> <field><op><value>`, `if <query> <field><op><value> then <command>`, `sample <query> [and <query>…] every <Nt> x<n> [clock=server]` (a row per query and run; client and server queries together run on the client's ticks), `sample <query> [and <query>…] every <Nt> [x<n>] while <action|wait>` (samples beside the action or wait) |

Video options an agent changes with `option` (graphics, render distance, particles, `hud`, …) apply at once, are never
written to `options.txt`, and are put back when the player or the agent leaves the world, when the player turns agent
control off (F8) and when the game closes; Blockpilot's own world switches (`join`, `create world`, snapshots) keep
them. The log names what was put back. Names are those of the running version's `options.txt`: from 26.1, where Fast,
Fancy and Fabulous became presets, `graphicsPreset`, `cutoutLeaves` and `improvedTransparency` take the place of
`graphicsMode`. A preset sets a dozen other options; `option reset` puts those back too.

The `integrated` commands, `snapshot` and `side=server` reads need a singleplayer world with commands allowed (cheats
on, or opened to LAN with cheats); `create world` makes one. That is deliberate: an agent playing a survival world
can't use them to cheat. While the game is paused (a pause screen in singleplayer), the server runs no ticks, so
commands that need them fail at once with `err paused` instead of holding up the queue. `snapshot restore` leaves the
world, copies the save back and rejoins, the same as quitting to the title screen and loading it again. Mods that keep
static state across worlds may therefore behave differently than after a fresh launch; relaunch the client when a test
needs full isolation.

### With Sable

Sable builds sub-levels: structures such as ships whose blocks live in a *plot*, a grid far out in the same dimension
(around x and z 20,000,000), and are drawn wherever the sub-level's pose puts them. With Sable installed, Blockpilot
works with them:

- A position in a plot prints as itself, followed by the sub-level and where that block is in the world, e.g.
  `aim:stone_button 20481032,129,20481033 sub=ship world=10.5,-58.5,6.5`. It pastes back into `block`, `blocks`,
  `nbt`, `face`, `use`, `mine`, `place` and `camera`. `status` shows `aboard:<name>` while the player stands on or
  rides a sub-level.
- `face` and `use` at a plot position aim where the sub-level is on each tick, so they work on a moving ship. As
  everywhere, `use` fails with `err obstructed <id> <position>` when something else is in the way; `use <pos> any`
  clicks it anyway, for multi-block panels whose mod takes a click on any of their blocks. A camera placed at, or looking at, a plot
  position rides along. `goto` refuses plot positions; `sable:tp` is the way aboard, and after `err obstructed`,
  `sable:tp` to the same position moves the player to the nearest spot from which it is in sight, if there is one
  (else it answers `hidden`).
- The `sable:` commands take a sub-level as its UUID, its name, or `here` (the one the player stands on or rides, else
  the one under the crosshair):
  - `sable:list` lists the sub-levels within 128 blocks, nearest first. In singleplayer with cheats on it adds
    `server_only=<n>` when the integrated server holds sub-levels in that radius that the client hasn't received
    (after a lost packet, say), and `sable:list side=server` lists the server's.
  - `sable:info here` gives the pose, velocity, height above ground (`agl`), bounds and whether the player rides it;
    `sable:info ship brief` only heading, pitch, roll, speed, climb rate, world y (`y`) and `agl`, e.g. for
    `sample sable:info ship brief every 10t x20` over a take-off. `agl` is measured from the lowest corner of the
    lowest block, however the sub-level is turned, to the ground straight under it. With `side=server` it reads the
    integrated server's copy (cheats on), also of a sub-level the client hasn't received, and measures `agl` in the
    server's level, so `sample sable:info ship brief side=server and <server-side query> every 20t x30` reads one
    copy in both rows. The size in `sable:list` and the plot bounds are the world bounds turned back into the plot, so
    they grow while the sub-level is turned.
  - `sable:find ship *door* 3` finds blocks aboard, with plot and world positions; with `side=server` from the
    server's copy, also on a sub-level the client hasn't received. They come nearest to the player first, nearest to
    `near=<pos>` (a world or plot position), or with `sort=-z` along the sub-level's own plot axes (`-` for largest
    first), so `sable:find ship *booster* 1 sort=-z` is the one furthest toward plot +z however the ship is turned. The
    limit cuts the ordered list, and `count=` gives all matches when it left some out.
  - `sable:tp 20481032,129,20481033` puts the player on a free spot next to that block aboard (cheats on): the
    nearest from which the block is in sight, so inside a hull rather than on it, else the nearest with `hidden`.
    `dist` is how far the player's eyes are from the block. `sable:tp ship *seat*` does the same next to the
    matching block aboard nearest the player, named in the reply as `block` and `target`; it reads the server's copy,
    so it also boards a ship that has flown out of the client's sight, without carrying a plot position along.
  - `sable:world 20481032.5,129.5,20481033.5` converts plot to world, and `sable:plot ship 10.5,-58.5,6.5` world to
    plot.

Blockpilot talks to Sable only through its published
[companion API](https://github.com/ryanhcode/sable-companion) and bundles nothing of it. Without Sable nothing changes
and nothing has to be installed; `help sable` says why the `sable:` commands are missing. Companion versions 1.x from
1.6 are supported (Sable 2.0.5 and 2.0.6 ship 1.6.0). With any other version the integration stays off, and the log
and `help sable` name the version found. Tested with Sable 2.0.6 (companion 1.6.0), Create 6.0.10 and Create
Aeronautics 1.3.2 on NeoForge 1.21.1. Sable has no release for 26.1 yet, so the 26.1 jars are built without the
integration; with a Sable installed they run as without it, and `help sable` says the build has no Sable integration.

## Extending it

Other mods can add namespaced commands that behave like built-in ones (scripts, `wait`, `sample`, JSON). Compile
against the API only (`compileOnly("dev.blockpilot:blockpilot-api:2.0.0")`, from mavenLocal as above) and register
them during mod loading, in a class only touched when Blockpilot is installed:

```java
if (ModList.get().isLoaded("blockpilot")) {
    BlockpilotIntegration.register();
}

// BlockpilotIntegration.java
BlockpilotApi.register("aerolarge", CommandSpec.query("gear", (context, args) ->
        Result.ok(new Fields().put("extended", Gear.isExtended(), "extended=" + Gear.isExtended())))
    .help("landing gear state").field("extended", "true when down"));
```

`aerolarge:gear` is then available, e.g. `wait aerolarge:gear until extended==true timeout=10s`.

Handlers run on the client thread. A command that touches the server world declares `onServer()`: its query runs at
the end of a server tick, and its action starts, ticks and is cancelled on the server thread. It needs the same
cheats world as `server`, and its queries work with `sample … clock=server` or beside client queries in one
`sample … and …`:

```java
BlockpilotApi.register("aerolarge", CommandSpec.action("build", (context, args) -> {
        Build build = Build.start(args.text("blueprint"));
        // null while still running; the result ends the action.
        return () -> build.done()
            ? Result.ok(new Fields().put("blocks", build.count(), "blocks=" + build.count()))
            : null;
    }).onServer().arg("blueprint", ArgType.TEXT).help("builds a blueprint at the player"));
```

A command declares its arguments as one or more forms, matched in order against a call's tokens; the first that
takes them all wins. A form is a sequence of keywords (`keyword`, bound to a name such as `op`), slots of one or more
slot types (`arg`, `optionalArg`; the first type that matches is used), optional groups (`optional`, taken when their
first part fits) and, last, free text (`ArgType.TEXT`). Options declare the slot types of their value, so a bad value
fails before the script runs, and a name ending in `:*` declares a family such as `rule:<name>=…`. A command with a
single form can declare its parts on the spec itself, as `build` does above:

```java
BlockpilotApi.register("aerolarge", CommandSpec.action("flaps", (context, args) -> {
        double degrees = args.word("op").equals("retract") ? 0 : args.number("degrees", 0);
        Duration over = args.has("ticks") ? args.duration("ticks") : new Duration(1);
        Flaps.move(args.option("side"), degrees, over.ticks());
        return () -> Flaps.settled() ? Result.ok() : null;
    })
    .form(form -> form.keyword("op", "set").arg("degrees", ArgType.NUMBER)
        .optionalArg("ticks", ArgType.DURATION))
    .form(form -> form.keyword("op", "retract"))
    .option("side", "one wing only", List.of("left", "right"))
    .help("moves the flaps").example("aerolarge:flaps set 15 20t"));
```

`help aerolarge:flaps` shows both forms, and `help grammar` prints `aerolarge.flaps-command ::= "aerolarge:flaps" (
"set" number duration? | "retract" )`. `args.type(name)` tells which slot type a token was taken as; the typed
accessors (`position`, `entity`, `integer`, `duration`, `point`, `uuid`, `index`, …) check it. Registration throws
`IllegalArgumentException` for a declaration the grammar can't reach (a name or option name it can't write, a keyword
such as `then`, two slots with one name, a type an earlier one always takes first, or an optional slot that would take
the next part's token), and freezes the spec. An example that doesn't parse, or doesn't start with the command's
`<namespace>:<name>`, gets the command refused with a logged error.

Extensions can emit events too: `BlockpilotApi.emit("aerolarge", "assembled", new Fields().put("uuid", id,
"uuid=" + id))` reaches every session as `! aerolarge:assembled uuid=…`. `BlockpilotApi.VERSION`, the version of
`blockpilot-api`, bumps its minor version for additions and its major version for breaking changes; declare the range
of Blockpilot versions your integration supports in your mod's metadata. Registrations made after mod loading has
finished are refused.

### Upgrading from blockpilot-api 1.x

2.0.0 replaces single-shape commands with forms and checks declarations, so a 1.x integration needs recompiling and
these changes:

- `arg` and `optionalArg` take one or more types; `choice(name, optional, words…)` is `keyword(name, words…)` or
  `optionalKeyword(name, words…)`, and further ways to write a command are `form(…)`s.
- `ArgType.CHOICE` is `KEYWORD`; `POINT`, `WIDGET`, `MENU_SLOT`, `UUID`, `ID`, `TAG`, `PATTERN` and `SECONDS` are new,
  and `WORD` no longer takes option- or predicate-shaped tokens such as `a=b` or `hp<5`.
- `Args.kind` is `Args.type`, and the accessors throw `IllegalStateException` when asked for a type the slot didn't
  take; `Args` is built from `Args.Value`s. `CommandSpec.options()` maps names to `CommandSpec.Option`s.
- Options are typed with `option(name, description, types…)` or `option(name, description, words, types…)`; one
  declared without types takes any value, which the handler checks (`intOption`, `numberOption`, `durationOption`).
  Every action takes `timeout=` already and can't declare it.
- A registered spec can't change; invalid declarations throw at registration.

## Building

```sh
./gradlew build
```

Gradle runs on JDK 25 and fetches the JDK each build needs. Every jar lands in `build/libs/`, tagged with the Minecraft
version and loader, e.g. `blockpilot-0.1.0-alpha.1+1.21.1-neoforge.jar` or `blockpilot-0.1.0-alpha.1+26.1.2-fabric.jar`.
`./gradlew :blockpilot:1.21.1-neoforge:runClient` (or `1.21.1-fabric`, `26.1-neoforge`, `26.1-fabric`) starts a dev
client.

The mod is one source tree built once per Minecraft version and loader: each build is a subproject named
`<minecraft>-<loader>` under `blockpilot/versions/` with its own `gradle.properties`. Loader-specific code lives in
`dev.blockpilot.platform.neoforge` and `.fabric` and only goes into that loader's jar; the rest reaches the loader
through `ClientPlatform`. `dev.blockpilot.api` and `dev.blockpilot.protocol` are free of Minecraft classes (enforced
by a test); `dev.blockpilot.game` holds the commands. The sources are checked in for `1.21.1-neoforge`. Code that
differs between builds is marked with Stonecutter comments such as `//? if neoforge {` or `//? if >=26.1 {`, and plain
renames (`ResourceLocation` to `Identifier`, …) are rules in `blockpilot/stonecutter.gradle.kts`. What differs most
between versions lives in `src/1.21.1/` and `src/26.1/`: the mixins, and a few classes with the same name and methods
in each (`CraftingRecipes`, `FrameCapture`).

To add a target: add it with `target(...)` in `settings.gradle.kts`, create
`blockpilot/versions/<minecraft>-{neoforge,fabric}/gradle.properties` (with `version_sources`), copy the nearest
`src/<version>/`, and add rename rules and `//? if` blocks for what changed, as was done for 26.1. A rename rule's new
text must not already appear in the shared sources: switching versions applies the rules backwards, which would
rename that text too. Set `sable_companion_version` only for versions Sable publishes its companion API for.

Measured on a dev client: `status` round trip over the line protocol about 15-30 ms.

## License

All Rights Reserved.

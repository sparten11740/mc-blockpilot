# Blockpilot

A Minecraft mod that lets AI agents and scripts observe and control the game through a fast, token-efficient local
interface. It runs in the client, for NeoForge and Fabric on 1.21.1, built from one source tree with
[Stonecutter](https://stonecutter.kikugie.dev/) the same way as Ultrasplosive. Agents play the game, or play-test a mod
under development: real input (clicks, holds, drags, keys), screenshots, structured results, condition waits, the
integrated server's commands with captured output, sound/message logs and performance captures.

## Using it

Put the jar for your loader into the client's `mods` folder (or a dev environment's `run/mods`) and start the game.
Once it is up, `<game dir>/blockpilot/endpoint.json` names the port (25599, or the next free one when several
instances run) and the token file. The endpoint listens on `127.0.0.1` only and needs the token.

**From a shell** (no setup, Python standard library only):

```sh
python3 tools/blockpilot.py --game-dir run --wait-ready 300 --wait-world "status; look"
python3 tools/blockpilot.py --game-dir run --json "inv"
```

`--wait-ready <seconds>` waits for the game to come up, and `--wait-world` also for the player to be in a world.
Exit codes: 0 every command returned `ok`, 1 one returned `err`, 2 the game could not be reached (or the wait timed
out).

**Over MCP** (Streamable HTTP; three tools: `run`, `screenshot`, `help`):

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
`<mod version>+<minecraft>`, e.g. `0.1.0-alpha.1+1.21.1`) and `dev.blockpilot:blockpilot-api`. In the other project,
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

Fabric Loom:

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

Then `python3 <blockpilot checkout>/tools/blockpilot.py --game-dir run --wait-ready 300 --wait-world "status"`, run
from that project, returns once the player is in the test world. In this repository the same properties go through Gradle:
`./gradlew :blockpilot:1.21.1-neoforge:runClient -Pblockpilot.autojoin=test -Pblockpilot.autocreate=flat`.

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
reply (`tick query`, `sample` rows, waits, `sounds`, `messages` and their `since=`) is the same clock: the integrated
server's game time in singleplayer, else the client's. `help`, `help <command|group>` and `help errors` give the full
reference.

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
| perception | `status` (one line: position, health, crosshair target, velocity, vehicle, camera angles…), `look [r]`, `find <block\|#tag\|*pattern\|entity> [r [limit]]`, `block <pos>`, `blocks <from> <to> [grid]` (`grid`: one id per cell), `inv [detail]` |
| observe | `screenshot [width] [burst=n every=Nt [sheet]] [crop=x0,y0,x1,y1] [hud=off]` (`sheet`: one PNG of the burst in a labelled grid), `pixels [[name=]x0,y0,x1,y1\|[name=]@<pos>]… [r=]` (mean rgb and luma, max and p90 luma of parts of the last frame), `option [<name> [<value>]]`, `option reset`, `camera at <pos> [look <pos\|e<id>>]` / `camera reset`, `sounds [since=] [match=] [limit=]`, `messages [since=] [kind=]`, `particles [since=]`, `log [level=] [since=] [grep=] [logger=] [limit=]`, `log mark`, `log detail <seq>` |
| movement | `face <pos\|e<id>>`, `turn <yaw> <pitch> [Nt]`, `move <dir> <blocks\|Nt>` (by blocks, `err stuck` after 20 ticks without progress), `jump`, `sneak on\|off`, `sprint on\|off`, `fly on\|off`, `goto <pos\|e<id>> [near=]` |
| interact | `use [target] [sneak] [hold=Nt]`, `attack [e<id>\|type] [x<n>]`, `mine [pos\|block\|#tag] [x<n>]`, `place <item> <pos>` |
| inventory | `select <slot\|item>`, `swap <a> <b>`, `drop <item\|slot> [n]`, `offhand <item>`, `equip <item>` |
| gui | `screen` (lists rows of lists as `w<i>` too), `cursor <x,y\|w<i>>`, `click <x,y\|w<i>\|s<slot>> [button]` (`w<i>`: a widget's centre, as `screen` numbers them), `close` |
| input | `mouse down\|up\|click <button>`, `mouse move <dx> <dy> [Nt]`, `drag <button> …`, `scroll <n>`, `key down\|up\|press <key\|key.mapping>`, `type <text>`, `release` |
| talk | `chat <text>`, `cmd <command>` |
| session | `worlds`, `servers`, `join world\|server <name>`, `leave`, `respawn`, `create world <name> [flat\|void\|normal] …`, `snapshot save\|restore\|delete <name>`, `snapshot list`, `delete world <folder>` (test worlds only) |
| integrated | `server [as=player] [settle=Nt] <command>` (result value and feedback lines), `nbt <pos\|e<id>\|uuid> [path=]`, `tick freeze\|unfreeze\|step <n>\|rate <tps>\|query`, `block`/`blocks`/`find … side=server` |
| performance | `perf start [name]`, `perf stop` (MSPT/TPS, FPS and 1% low, time per block-entity and entity type, per renderer, per particle type with live counts, per render stage on NeoForge, network; JSON summary saved to `blockpilot/perf/<name>.json`), `perf status` |
| sable | with Sable only: `sable:list [r]`, `sable:info <sub>`, `sable:find <sub> <block\|#tag\|*pattern> [limit]`, `sable:world <pos>`, `sable:plot <sub> <pos>`, `sable:tp <pos>` (see [With Sable](#with-sable)) |
| meta | `help`, `help grammar`, `fmt text\|json`, `sub on\|off`, `sub log <level>\|off`, `stop`, `wait <Nt>`, `wait <query> until <field><op><value> [clock=server]`, `assert <query> <field><op><value>`, `if <query> <field><op><value> then <command>`, `sample <query> every <Nt> x<n> [clock=server]`, `sample <query> every <Nt> [x<n>] while <action|wait>` (samples beside the action or wait) |

Video options an agent changes with `option` (graphics, render distance, particles, `hud`, …) apply at once, are never
written to `options.txt`, and are put back when the player or the agent leaves the world, when the player turns agent
control off (F8) and when the game closes; Blockpilot's own world switches (`join`, `create world`, snapshots) keep
them. The log names what was put back.

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
  `face`, `use` and `camera`. `status` shows `aboard:<name>` while the player stands on or rides a sub-level.
- `face` and `use` at a plot position aim where the sub-level is on each tick, so they work on a moving ship. `use`
  fails with `err obstructed <id> <position>` when something else is in the way. A camera placed at, or looking at, a
  plot position rides along. `goto` refuses plot positions; `sable:tp` is the way aboard.
- The `sable:` commands take a sub-level as its UUID, its name, or `here` (the one the player stands on or rides, else
  the one under the crosshair):
  - `sable:list` lists the sub-levels within 128 blocks, nearest first.
  - `sable:info here` gives the pose, velocity, bounds and whether the player rides it.
  - `sable:find ship *door* 3` finds blocks aboard, with plot and world positions.
  - `sable:tp 20481032,129,20481033` puts the player on a free spot next to that block aboard (cheats on).
  - `sable:world 20481032.5,129.5,20481033.5` converts plot to world, and `sable:plot ship 10.5,-58.5,6.5` world to
    plot.

Blockpilot talks to Sable only through its published
[companion API](https://github.com/ryanhcode/sable-companion) and bundles nothing of it. Without Sable nothing changes
and nothing has to be installed; `help sable` says why the `sable:` commands are missing. Companion versions 1.x from
1.6 are supported (Sable 2.0.5 and 2.0.6 ship 1.6.0). With any other version the integration stays off, and the log
and `help sable` name the version found. Tested with Sable 2.0.6 (companion 1.6.0) on NeoForge 1.21.1.

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
cheats world as `server`, and its queries work with `sample … clock=server`:

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
version and loader, e.g. `blockpilot-0.1.0-alpha.1+1.21.1-neoforge.jar`. `./gradlew :blockpilot:1.21.1-neoforge:runClient`
(or `-fabric`) starts a dev client.

The mod is one source tree built once per Minecraft version and loader: each build is a subproject named
`<minecraft>-<loader>` under `blockpilot/versions/` with its own `gradle.properties`. Loader-specific code lives in
`dev.blockpilot.platform.neoforge` and `.fabric` and only goes into that loader's jar; the rest reaches the loader
through `ClientPlatform`. `dev.blockpilot.api` and `dev.blockpilot.protocol` are free of Minecraft classes (enforced
by a test); `dev.blockpilot.game` holds the commands. Code that differs between builds is marked with Stonecutter
comments such as `//? if neoforge {`; mixins, whose targets differ most between versions, live in `src/1.21.1/`.

To add a target such as 26.1: add `target("26.1", "neoforge", "fabric")` in `settings.gradle.kts`, create
`blockpilot/versions/26.1-{neoforge,fabric}/gradle.properties` (with `version_sources=26.1`), add rename rules in
`blockpilot/stonecutter.gradle.kts`, and copy `src/1.21.1/` to `src/26.1/`, updating the mixins and the few
version-sensitive calls (input callbacks moved to records in 1.21.9, recipe placement in 1.21.2, entity NBT saving in
1.21.6).

Measured on a dev client: `status` round trip over the line protocol about 15-30 ms.

## License

All Rights Reserved.

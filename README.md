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

**Line protocol** (`nc 127.0.0.1 25599`): send `auth <token>`, then one script per line, optionally prefixed with
`#<id> `. Text replies end with a line holding only `.`; after `fmt json` each reply is one JSON line. `sub on` pushes
events as they happen.

The window can stay in the background: while an agent is connected the game does not pause and keeps handling input.
Real keyboard or mouse input to the focused window cancels running agent actions; F8 toggles agent control off and on.
A line in the top-left corner shows when an agent is connected or acting.

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
chat, actionbar, screen, died, …) arrive as `! …` lines with the next reply. `help`, `help <command|group>` and
`help errors` give the full reference.

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
| perception | `status` (one line: position, health, crosshair target, velocity, vehicle, camera angles…), `look [r]`, `find <block\|#tag\|*pattern\|entity> [r] [limit]`, `block <pos>`, `blocks <from> <to>`, `inv [detail]` |
| observe | `screenshot [width] [burst=n every=Nt] [hud=off]`, `camera at <pos> [look <pos\|e<id>>]` / `camera reset`, `sounds [since=] [match=] [limit=]`, `messages [since=] [kind=]`, `particles [since=]` |
| movement | `face <pos\|e<id>>`, `turn <yaw> <pitch> [Nt]`, `move <dir> <blocks\|Nt>`, `jump`, `sneak on\|off`, `sprint on\|off`, `fly on\|off`, `goto <pos\|e<id>> [near=]` |
| interact | `use [target] [sneak] [hold=Nt]`, `attack [e<id>\|type] [x<n>]`, `mine [pos\|block\|#tag] [x<n>]`, `place <item> <pos>` |
| inventory | `select <slot\|item>`, `swap <a> <b>`, `drop <item\|slot> [n]`, `offhand <item>`, `equip <item>` |
| gui | `screen`, `cursor <x,y>`, `click <x,y\|s<slot>> [button]`, `close` |
| input | `mouse down\|up\|click <button>`, `mouse move <dx> <dy> [Nt]`, `drag <button> …`, `scroll <n>`, `key down\|up\|press <key\|key.mapping>`, `type <text>`, `release` |
| talk | `chat <text>`, `cmd <command>` |
| session | `worlds`, `servers`, `join world\|server <name>`, `leave`, `respawn`, `create world <name> [flat\|void\|normal] …` |
| integrated | `server <command> [as=player]` (result value and feedback lines), `nbt <pos\|e<id>\|uuid> [path=]`, `tick freeze\|unfreeze\|step <n>\|rate <tps>\|query` |
| performance | `perf start [name]`, `perf stop` (JSON summary saved to `blockpilot/perf/<name>.json`), `perf status` |
| meta | `help`, `fmt text\|json`, `sub on\|off`, `stop`, `wait <Nt>`, `wait <query> until <field><op><value>`, `sample <query> every <Nt> x<n>` |

`server`, `nbt` and `tick` need a singleplayer world with commands allowed (cheats on); `create world` makes one.

## Extending it

Other mods can add namespaced commands that behave like built-in ones (scripts, `wait`, `sample`, JSON). Compile
against the API only (`compileOnly("dev.blockpilot:blockpilot-api:1.0.0")`, from mavenLocal as above) and register
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

# Design

## Context

This change builds on `add-agent-control-mod`: the Stonecutter tree, the
`api`/`protocol`/`game`/`platform` layering (D2), the structured `Result` model
and formatters (D5), the action queue (D6) and the extension API (D12).
Blockpilot is a client mod. In singleplayer (and when hosting via Open to LAN),
the integrated `MinecraftServer` runs in the same JVM, reachable through
`Minecraft.getSingleplayerServer()`, and its lifecycle and tick events fire
through the normal loader event buses. The current RCON-based workflow and its
pain points are summarized in `proposal.md`.

## Goals / Non-Goals

**Goals:**
- Every server-side feature works the same on NeoForge and Fabric, through the
  platform seam.
- Server-side reads and writes happen only on the server thread. Client-side
  code never touches server objects directly.
- Keep the code that differs between Minecraft versions (NBT saving, world
  creation) in a few per-version files, ready for 26.1.

**Non-Goals:**
- Dedicated servers and fake players. A later change adds them, reusing
  `server-bridge` and `tick-control` behind a server-side endpoint.
- Sable-specific commands. A later compat change adds them through the extension
  API.
- Partial (region-only) snapshots, and snapshots while the world stays loaded.

## Decisions

### D1. The gate: an integrated server and an operator player

A command is allowed when `Minecraft.getSingleplayerServer()` is non-null and
the local player's permission level on it is ≥ 2. This is true when the world
allows commands, and also when cheats were turned on through Open to LAN. Using
the permission level instead of reading `allowCommands` from world data works
the same way on every version. Gating happens once in the dispatcher, from the
`needsServer()` flag on `CommandSpec` (which `onServer()` sets too), so new
commands can't forget it. `side=server` reads check the same two conditions
themselves.

### D2. Server-thread execution through the engine

The server tick-end hook drives the same engine as the client tick (D7). A
command flagged `onServer()` starts, ticks and is cancelled only on the server
thread. A server-side query runs there in one piece at the end of a tick, so its
values come from a single point between ticks (spec: consistent within a tick).
The network thread never blocks on the server.

The gate reads client state, so it is checked on a client tick. It is checked
there for the whole run of queries up to the next action at once: queries change
nothing, so their gates still hold when they run. A script of server-side
queries is then answered in one server tick instead of one query per tick (1,000
`nbt` queries took 50 s before, now under 0.1 s).

`side=server` on `block`, `blocks` and `find` instead submits the read with
`server.submit(...)` from the client thread and waits at most 5 s for it (`err
timeout` otherwise).

*Deviation:* no `ServerCall` class. Queue affinity covers server commands, and
`Sides.read` covers `side=server`.

### D3. `server <command>` capture

A `CommandSourceStack` is built from `server.createCommandSourceStack()` (or,
with `as=player`, from the `ServerPlayer`'s own source), with permission 4. Its
`CommandSource` collects `sendSystemMessage` text (plain
`Component.getString()`) and accepts success and failure output but not
`informAdmins`. A `CommandResultCallback` records `success` and `result`.
Execution goes through `server.getCommands().performPrefixedCommand(source,
command)`. Syntax and execution failures are collected through `sendFailure` and
become `err command_failed <message>`. The message is the last feedback line
that isn't Brigadier's `…<--[HERE]` pointer (found by its `command.context.here`
translation key, at any depth of the component).

The server sends block and entity changes to the client during its next tick,
so the reply waits `settle=` server ticks (default 2) before answering. A client
query right after `server` then sees the change. Probes pass `settle=0t`.
Options come before the command text, which runs to the end of the command.

Feedback a mod sends straight to players instead of the source isn't captured.
It still reaches the agent as `! chat` events. This is documented in `help
server`.

*Alternative:* bundling an RCON client or opening RCON on the integrated server.
Rejected: it adds a second protocol and port, and still returns only flat text.

### D4. Server-side perception reuses the client implementations

`block`, `blocks` and `find` take a `Level`, which is a `ClientLevel` or a
`ServerLevel`, so `side=server` changes only which level is passed and on which
thread the command runs. The loaded-chunk check is
`getChunkSource().getChunkNow(x, z) != null`, which never loads a chunk.
`Level.hasChunkAt` can't be used: on the client it answers true for every chunk.
On the server, `getChunkNow` answers only on the server thread. Server-side
entity rows add `uuid`. Entity ids match between the integrated server and the
client for tracked entities. A client-only entity simply isn't found on the
server side (`err no_entity`).

### D5. NBT

- Block entities: `saveWithFullMetadata(registries)`.
- Entities: 1.21.1 uses `saveWithoutId(CompoundTag)`. It is called from common
  code for now, and moves to `src/<version>/` with the first target that uses
  `ValueOutput` (1.21.6+).
- `path=` is parsed with vanilla's `NbtPathArgument`.
- Output: text mode shows the tag's compact SNBT, and JSON mode converts with
  `NbtOps` → `JsonOps`. The JSON field is `value`, so `wait`/`sample` predicates
  can use it.
- The 8 KB cap is measured on the rendered SNBT. Over it, `value` is the first
  8,192 characters as a string (in JSON too), and `truncated` reports the full
  length.

### D6. Tick control on `ServerTickRateManager`

`server.tickRateManager()` (available since 1.20.3, so on every target):
- `setFrozen`, `setTickRate` and `stepGameIfPaused(n)` do the work. Vanilla
  already syncs the ticking state to the client.
- `tick step n` records the overworld game time and completes on the server
  tick-end hook once it has risen by n and `isSteppingForward()` is false. The
  server's own tick counter can't be used, because it keeps rising while
  frozen.
- Blockpilot records any freeze or rate change it made, and resets the server
  to unfrozen at 20 tps from the server-stopping hook, before the world saves.

### D7. Thread affinity in the one action queue

There is no separate scheduler. The engine's FIFO queue (D6 of the first
change) is ticked from both hooks. Each running action has a thread affinity:
client-affinity actions tick on client ticks and server-affinity actions on
server ticks, with the queue head guarded by a lock. Server commands,
server-thread extension actions, `tick step`, `sample … clock=server` and
server-clock `wait` have server affinity. The gate is always checked on a client
tick first, because it reads client state. Cancelling a server-affinity action
runs its `cancel` on the server thread too (`GameBridge.runOnServer`).

While the server is frozen, vanilla still calls the tick-end hook on every loop
iteration (only the levels stop ticking). A server-clock wait therefore keeps
re-checking, its `stable=` and `timeout=` count those iterations, and it times
out normally instead of hanging. `tick step` can't run during such a wait,
because both are actions in the same queue; to watch stepped ticks, step first,
then read or sample.

*Deviation:* the plan assumed the hook runs only for stepped ticks while frozen,
so that waits would advance through `tick step`. That isn't how vanilla
behaves, and skipping frozen ticks would make a wait at the queue head block
every step forever.

### D8. Log capture with a Log4j2 appender

At mod construction Blockpilot adds an appender to the root `LoggerConfig` (INFO
and above) through the live `LoggerContext`. Every 200 client ticks (10 s) it
checks that the appender is still attached and re-attaches it if a loader
reconfigured logging. There is no startup self-test; the periodic check
replaced it.
- The appender writes immutable entries into a fixed 5,000-slot ring buffer
  under a short lock.
- Condensed traces are built at append time (errors are rare). The full trace is
  kept as a string for `log detail`.
- Frames from `java.`, `jdk.`, `sun.`, `org.apache.logging.`, `org.slf4j.` and
  `com.mojang.logging.` are skipped when picking the three frames shown.
- Blockpilot logs nothing per request (connection failures go to DEBUG, below
  the capture level), so capture can't feed on itself. A crashing command's
  ERROR is captured like any other.
- Live delivery uses a per-session minimum level and a 20/s window. Entries
  held back are reported as one `log_dropped` event with the next reply.

### D9. Test worlds

- **Creation:** `createWorldOpenFlows().createFreshLevel(...)` with
  `LevelSettings(allowCommands = true, game mode, difficulty)`,
  `WorldOptions(seed)`, and the `FLAT` world preset. `void` uses the flat preset
  with the vanilla "the void" layer settings. The creation call is in common
  code for now and moves to `src/<version>/` with the first target whose
  signature or presets differ.
- **Difficulty:** `peaceful`, `easy` and `hard` are bare words. A bare `normal`
  names the world type, so normal difficulty is written `difficulty=normal`.
- **Game rules** are applied after joining, as `server gamerule <rule> <value>`
  through D3. That stays independent of how each version represents game rules
  (1.21.11 made them a registry).
- **Marker:** `<save>/blockpilot-test.json` (creation time, Blockpilot version).

### D10. Snapshots by leave/copy/rejoin

- **Storage:** `<gameDir>/blockpilot/snapshots/<save folder>/<snapshot>/`.
- **Save:** remember the folder, `leave` (the integrated server stops and saves
  everything, including other mods' `SavedData` and files), copy the save folder
  without `session.lock`, then `join world`.
- **Restore:** leave, rename the live save to a temp name, copy the snapshot in,
  rejoin, then delete the temp folder. If the copy fails, the temp folder is
  moved back.
- **Names:** world names must match `[A-Za-z0-9 _.-]{1,64}`, snapshot names the
  same without spaces. Neither may start with `.`, and both must resolve inside
  their parent directory. This blocks path traversal from agent input.
- **Timeouts:** default 300 s. Results report the size and duration.

*Alternative:* copying while running with `save-off`/`save-all flush`. Rejected,
because mods that keep state in memory or write their own files outside
`SavedData` (Sable's sub-level storage is the motivating case) wouldn't be
captured reliably.

### D11. Readiness file and auto-join

- **`endpoint.json`:** written atomically (temp file + move) right after the
  listener binds. Removed in the client-stopping hook; a killed process leaves
  it behind, and clients recognize that by the dead `pid`. Contains `port`,
  `pid`, `mod`, `api`, `minecraft`, `loader` and `token_file`.
- **Auto-join:** runs from the platform screen-init hook the first time a
  `TitleScreen` or `AccessibilityOnboardingScreen` opens. It clears
  `onboardAccessibility`, then calls the same code as `create world` / `join
  world`. If that fails, it logs and stays on the title screen.

Vanilla's `--quickPlaySingleplayer` is kept as an option but can't create
worlds, which is why Blockpilot has its own property.

### D12. The CLI: `tools/blockpilot.py`

One Python 3 script using only the standard library, so any machine with Python
runs it without a build step.
- It reads `endpoint.json` and the token from `token_file`. If the recorded
  `pid` is no longer running, the file is stale and the game counts as not
  reachable (skipped on Windows, where probing a pid isn't safe).
- It connects to the line protocol, sends `auth`, optionally `fmt json`, then
  the script, and prints the reply until the `.` terminator (text) or the single
  JSON line.
- The exit code follows the spec.
- `--wait-ready <seconds>` retries the file and a TCP connect. `--wait-world`
  then polls `status` once a second until it isn't `menu`, within the same
  deadline, and names the last status if time runs out.

*Deviation:* the plan was a JDK-only Java jar outside the Stonecutter tree. A
Python script does the same with no build step or artifact to publish.

### D13. Publishing for other dev environments

`maven-publish` in both node build scripts publishes
`dev.blockpilot:blockpilot-<loader>:<mod_version>+<minecraft>`. Fabric publishes
the remapped jar (the remapped one for 1.21.x). The extension API is published
as `dev.blockpilot:blockpilot-api:<api version>`. README snippets:
- ModDevGradle: `localRuntime("dev.blockpilot:blockpilot-neoforge:…")`, plus
  `systemProperty("blockpilot.autojoin", …)` in a run.
- Loom: `modLocalRuntime(...)` and `vmArg(...)`.

### D14. Server-thread extension handlers

The extension API gains `CommandSpec.onServer()`. Such commands need the server
(D1). Their queries run at the end of a server tick (D2), and their actions get
server affinity in the queue, including `cancel` (D7). The API stays
Minecraft-free. Handlers find the server themselves; Blockpilot only guarantees
the thread and the gate. The API was still unreleased when `onServer()` was
added, so it ships in 1.0.0 instead of a minor version bump.

### D15. Platform seam additions

`ClientPlatform.Hooks` gains integrated-server hooks (server tick end, server
stopping) and client hooks (screen init, client stopping). A server-started hook
was planned but nothing needed it, so it was left out.

- Server stopping: Fabric `ServerLifecycleEvents.SERVER_STOPPING`; NeoForge
  `ServerStoppingEvent`.
- Server tick end: Fabric `ServerTickEvents.END_SERVER_TICK`; NeoForge
  `ServerTickEvent.Post`.
- Screen init: Fabric `ScreenEvents.AFTER_INIT`; NeoForge
  `ScreenEvent.Init.Post`.
- Client stopping: Fabric `ClientLifecycleEvents.CLIENT_STOPPING`; NeoForge
  `GameShuttingDownEvent`.

### D16. One clock for tick numbers

`GameClock.now()` is the integrated server's overworld game time in
singleplayer, otherwise the client level's. Every tick number in a reply uses
it: `tick` commands, sample rows, waits, and the observation logs with their
`since=`. The client's own copy lags the server's by a tick or more, and
mixing the two made a `since=` taken from `tick query` miss events (reported by
a play-testing agent). Observation entries recorded on the client thread
read the server's counter, a plain monotonic long.

## Risks / Trade-offs

- [Mods that keep static state across worlds may behave differently after a
  snapshot restore than after a fresh launch.] → A restore is the same as
  quitting to the title screen and reloading, which mods must already handle.
  The README warns about it, and for full isolation the agent can relaunch the
  client.
- [Snapshots of large worlds are slow.] → Test worlds are flat and small by
  default. Size and duration are reported, and the timeout is configurable.
- [Feedback sent to players instead of the command source isn't in `server`
  results.] → It arrives as `! chat` events, which is documented.
- [The loader replaces the Log4j configuration and drops the appender.] →
  Checked every 10 s and re-attached (D8).
- [`clock=server` sampling with expensive queries slows server ticks.] → Same
  caps as client queries. `perf` shows the server tick time when in doubt.
- [The cheats gate blocks a legitimate test in a survival world.] → The agent
  can turn cheats on through Open to LAN, or test in a `create world` test
  world. The gate is deliberate, so agents playing a survival world can't cheat.
- [Agent-supplied names could escape the saves or snapshot directories.] →
  Strict name validation and resolved-path checks (D10).

## Migration Plan

Additive, on top of `add-agent-control-mod`. Apply and archive it after that
change. The extension API stays at 1.0.0 (D14). Nothing to roll back beyond
removing the jar. Snapshots and test worlds are plain folders the user can
delete.

## Open Questions

- Whether `snapshot save` should also offer a faster live mode later, for mods
  known to keep all their state in `SavedData`. It can be added as an option
  without changing anything here.

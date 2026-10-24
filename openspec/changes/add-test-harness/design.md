# Design

## Context

This change builds on `add-agent-control-mod`: the Stonecutter tree, the `api`/`protocol`/`game`/`platform` layering (D2), the structured `Result` model and formatters (D5), the action queue (D6) and the extension API (D12). Blockpilot is a client mod. In singleplayer (and when hosting via Open to LAN), the integrated `MinecraftServer` runs in the same JVM, reachable through `Minecraft.getSingleplayerServer()`, and its lifecycle and tick events fire through the normal loader event buses. The current RCON-based workflow and its pain points are summarized in `proposal.md`.

## Goals / Non-Goals

**Goals:**
- Every server-side feature works the same on NeoForge and Fabric, through the platform seam.
- Server-side reads and writes happen only on the server thread. Client-side code never touches server objects directly.
- Keep the code that differs between Minecraft versions (NBT saving, world creation) in a few per-version files, ready for 26.1.

**Non-Goals:**
- Dedicated servers and fake players. A later change adds them, reusing `server-bridge` and `tick-control` behind a server-side endpoint.
- Sable-specific commands. A later compat change adds them through the extension API.
- Partial (region-only) snapshots, and snapshots while the world stays loaded.

## Decisions

### D1. The gate: an integrated server and an operator player

A command is allowed when `Minecraft.getSingleplayerServer()` is non-null and the local player's permission level on it is ≥ 2. This is true when the world allows commands, and also when cheats were turned on through Open to LAN. Using the permission level instead of reading `allowCommands` from world data works the same way on every version. Gating happens once in the dispatcher, from a `requiresServer` flag in the registry, so new commands can't forget it.

### D2. One server-call path

`ServerCall.run(server -> …)` submits work with `server.submit(...)` and completes the session's `CompletableFuture` on the server thread. The network thread never blocks on the server. A server-side query is one submitted task, so its values come from a single point between ticks (spec: consistent within a tick).

### D3. `server <command>` capture

A `CommandSourceStack` is built from `server.createCommandSourceStack()` (or, with `as=player`, from the `ServerPlayer`'s own source), with permission 4. Its `CommandSource` collects `sendSystemMessage` text (plain `Component.getString()`) and accepts success and failure output but not `informAdmins`. A `CommandResultCallback` records `success` and `result`. Execution goes through `server.getCommands().performPrefixedCommand(source, command)`. Syntax and execution failures are collected through `sendFailure` and become `err command_failed <message>`.

Feedback a mod sends straight to players instead of the source isn't captured. It still reaches the agent as `! chat` events. This is documented in `help server`.

*Alternative:* bundling an RCON client or opening RCON on the integrated server. Rejected: it adds a second protocol and port, and still returns only flat text.

### D4. Server-side perception reuses the client implementations

`block`, `blocks` and `find` are rewritten against a `LevelView`: a `Level`, a loaded-chunk check (`getChunkSource().getChunkNow(x, z) != null`, never loading), and an entity lookup. `ClientLevel` and `ServerLevel` both satisfy it, so `side=server` changes only which view is passed and on which thread the command runs. Server-side entity rows add `uuid`. Entity ids match between the integrated server and the client for tracked entities. A client-only entity simply isn't found on the server side (`err no_entity`).

### D5. NBT

- Block entities: `saveWithFullMetadata(registries)`.
- Entities: the per-version save call. 1.21.1 uses `saveWithoutId(CompoundTag)`, and 1.21.6+ uses `ValueOutput`, so it goes in `src/<version>/`.
- `path=` is parsed with vanilla's `NbtPathArgument`.
- Output: text mode shows the tag's compact SNBT, and JSON mode converts with `NbtOps` → `JsonOps`. The JSON field is `value`, so `wait`/`sample` predicates can use it.
- The 8 KB cap is measured on the rendered text, and the truncation marker reports the full size.

### D6. Tick control on `ServerTickRateManager`

`server.tickRateManager()` (available since 1.20.3, so on every target):
- `setFrozen`, `setTickRate` and `stepGameIfPaused(n)` do the work. Vanilla already syncs the ticking state to the client.
- `tick step n` records `server.getTickCount()` and completes on the server tick-end hook once the count has risen by n and `isSteppingForward()` is false.
- Blockpilot records any freeze or rate change it made, and resets it from the server-stopping hook, before the world saves.

### D7. A server scheduler for `clock=server`

A `ServerScheduler` runs on the server tick-end hook. It drives `sample … clock=server`, server-clock `wait`, `tick step` completion, and server-thread extension actions. `sample` and server-clock `wait` are actions in the shared FIFO queue (ordering as in D6 of the first change). Each action has a thread affinity: the queue runner ticks client-affinity actions on client ticks and server-affinity actions on server ticks, with the queue head guarded by a lock. While the server is frozen, its tick-end hook runs only for stepped ticks, so server-clock waits advance only through `tick step`, as the spec requires.

### D8. Log capture with a Log4j2 appender

At mod construction Blockpilot adds an appender to the root `LoggerConfig` (INFO and above) through the live `LoggerContext`. It re-attaches from a configuration-change listener if the loader reconfigures logging.
- The appender writes immutable entries into a fixed 5,000-slot ring buffer under a short lock.
- Condensed traces are built at append time (errors are rare). The full trace is kept as a string for `log detail`.
- Frames from `java.`, `jdk.`, `sun.`, `org.apache.logging.`, `org.slf4j.` and `com.mojang.logging.` are skipped when picking the three frames shown.
- Blockpilot's own endpoint logging goes to a logger the appender ignores, which prevents feedback loops.
- Live delivery uses a per-session minimum level and a 20/s token bucket.

### D9. Test worlds

- **Creation:** `createWorldOpenFlows().createFreshLevel(...)` with `LevelSettings(allowCommands = true, game mode, difficulty)`, `WorldOptions(seed)`, and the `FLAT` world preset. `void` uses the flat preset with the vanilla "the void" layer settings. The creation call goes in `src/<version>/`, because its signature and presets moved between versions.
- **Game rules** are applied after joining, as `server gamerule <rule> <value>` through D3. That stays independent of how each version represents game rules (1.21.11 made them a registry).
- **Marker:** `<save>/blockpilot-test.json` (creation time, Blockpilot version).

### D10. Snapshots by leave/copy/rejoin

- **Storage:** `<gameDir>/blockpilot/snapshots/<save folder>/<snapshot>/`.
- **Save:** remember the folder, `leave` (the integrated server stops and saves everything, including other mods' `SavedData` and files), copy the save folder without `session.lock`, then `join world`.
- **Restore:** leave, rename the live save to a temp name, copy the snapshot in, rejoin, then delete the temp folder. If the copy fails, the temp folder is moved back.
- **Names:** snapshot and world names must match `[A-Za-z0-9 _.-]{1,64}`, not be `.` or `..`, and resolve inside their parent directory. This blocks path traversal from agent input.
- **Timeouts:** default 300 s. Results report the size and duration.

*Alternative:* copying while running with `save-off`/`save-all flush`. Rejected, because mods that keep state in memory or write their own files outside `SavedData` (Sable's sub-level storage is the motivating case) wouldn't be captured reliably.

### D11. Readiness file and auto-join

- **`endpoint.json`:** written atomically (temp file + move) right after the listener binds. Removed in the client-stopping hook. Contains `port`, `pid`, `mod`, `api` and `token_file`.
- **Auto-join:** runs from the platform screen-init hook the first time a `TitleScreen` or `AccessibilityOnboardingScreen` opens. It clears `onboardAccessibility`, then calls the same code as `create world` / `join world`. If that fails, it logs and stays on the title screen.

Vanilla's `--quickPlaySingleplayer` is kept as an option but can't create worlds, which is why Blockpilot has its own property.

### D12. `blockpilot-cli`

A plain Java project outside the Stonecutter tree (`blockpilot-cli/`), using only the JDK and compiled for Java 17 so any modern JVM runs it.
- It reads `endpoint.json` with a minimal flat-object reader and the token from `token_file`.
- It connects to the line protocol, sends `auth`, optionally `fmt json`, then the script as one `#1` request, and prints the reply until the `.` terminator (text) or the single JSON line.
- The exit code follows the spec.
- `--wait-ready` polls the file and a TCP connect. `--wait-world` polls `status` until it isn't `menu`.

It doesn't reuse the `protocol` package, which would pull in Netty and Gson; the line protocol is simple enough to speak directly.

### D13. Publishing for other dev environments

`maven-publish` in both node build scripts publishes `dev.blockpilot:blockpilot-<loader>:<mod_version>+<minecraft>`. Fabric publishes the remapped jar (the remapped one for 1.21.x). The CLI is published as `dev.blockpilot:blockpilot-cli`. README snippets:
- ModDevGradle: `localRuntime("dev.blockpilot:blockpilot-neoforge:…")`, plus `jvmArgument("-Dblockpilot.autojoin=…")` in a run.
- Loom: `modLocalRuntime(...)` and `vmArg(...)`.

### D14. Server-thread extension handlers

The extension API gains `CommandSpec.onServer()`. Such commands are flagged `requiresServer` (D1). Their query handlers run through `ServerCall` (D2), and their actions get server affinity in the queue (D7). The API stays Minecraft-free. Handlers find the server themselves; Blockpilot only guarantees the thread and the gate. This is an additive API change, so it is a minor API version bump.

### D15. Platform seam additions

The common `Platform` gains integrated-server hooks: server started, server tick end, server stopping. `ClientPlatform` gains screen-init and client-stopping hooks.

| Hook | Fabric | NeoForge |
|---|---|---|
| Server started / stopping | `ServerLifecycleEvents` | `ServerStartedEvent` / `ServerStoppingEvent` |
| Server tick end | `ServerTickEvents.END_SERVER_TICK` | `ServerTickEvent.Post` |
| Screen init | `ScreenEvents.AFTER_INIT` | `ScreenEvent.Init.Post` |
| Client stopping | `ClientLifecycleEvents.CLIENT_STOPPING` | `GameShuttingDownEvent` |

## Risks / Trade-offs

- [Mods that keep static state across worlds may behave differently after a snapshot restore than after a fresh launch.] → A restore is the same as quitting to the title screen and reloading, which mods must already handle. The README warns about it, and for full isolation the agent can relaunch the client.
- [Snapshots of large worlds are slow.] → Test worlds are flat and small by default. Size and duration are reported, and the timeout is configurable.
- [Feedback sent to players instead of the command source isn't in `server` results.] → It arrives as `! chat` events, which is documented.
- [The loader replaces the Log4j configuration and drops the appender.] → Re-attach on configuration change. A startup self-test logs a marker line and checks that it was captured.
- [`clock=server` sampling with expensive queries slows server ticks.] → Same caps as client queries, and a warning when one server-side evaluation takes over 2 ms.
- [The cheats gate blocks a legitimate test in a survival world.] → The agent can turn cheats on through Open to LAN, or test in a `create world` test world. The gate is deliberate, so agents playing a survival world can't cheat.
- [Agent-supplied names could escape the saves or snapshot directories.] → Strict name validation and resolved-path checks (D10).

## Migration Plan

Additive, on top of `add-agent-control-mod`. Apply and archive it after that change. The extension API version bumps its minor version (D14). Nothing to roll back beyond removing the jar. Snapshots and test worlds are plain folders the user can delete.

## Open Questions

- Whether `snapshot save` should also offer a faster live mode later, for mods known to keep all their state in `SavedData`. It can be added as an option without changing anything here.

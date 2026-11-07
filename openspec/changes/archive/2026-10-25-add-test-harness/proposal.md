# Proposal

## Why

Agents play-test mods in development by driving a dedicated server through RCON.
They parse chat text with grep, time things with `sleep`, use `execute if block`
batches to probe the world, use marker entities to read positions, and have no
way to reset the world between runs. `add-agent-control-mod` gives them a real
player, screenshots, structured results and condition waits. But a test also
needs the server's side: running commands and reading their results, full NBT,
deterministic ticks, the log, and repeatable worlds. In singleplayer the
integrated server runs in the same JVM as Blockpilot, so the mod can provide all
of this directly, with no RCON and no second process.

This change builds on `add-agent-control-mod` and must be applied and archived
after it.

## What Changes

- **Server bridge**, when an integrated server is running:
  - `server <command>` runs a command on the server thread and returns its
    result value, success flag and feedback lines as a structured result (text
    or JSON).
  - `nbt <pos|e<id>|uuid> [path=…]` reads block-entity or entity data as
    SNBT/JSON.
  - `side=server` on `block`, `blocks` and `find` reads the server level
    directly, including forceloaded chunks the client hasn't loaded.
  - Server-side entity results carry UUIDs.
- **Tick control**:
  - `tick freeze|unfreeze|rate <tps>|query` wraps the vanilla tick manager.
  - `tick step <n>` returns only after the server has actually run n ticks.
  - `sample <query> every <Nt> x<n> clock=server` records server-side values on
    exact server ticks, and `clock=server` on `wait … until` makes condition
    checks deterministic.
- **Log stream**:
  - `log mark` and `log [level=…] [since=<mark>] [grep=…]` read the shared
    client/server log with condensed stack traces, and `log detail <seq>`
    returns a full trace.
  - `sub log <level>` pushes `! log …` events as they happen.
- **Test worlds**:
  - `create world <name> [flat|void|normal] [peaceful|easy|hard|difficulty=…]
    [mode=creative|survival] [seed=…] [rule:<gamerule>=<value>…]` creates a
    world with cheats on and joins it.
  - `snapshot save|restore|list|delete <name>` copies the whole save folder
    (including other mods' data, such as Sable's) through leave/copy/rejoin.
  - `delete world <folder>` works only on worlds Blockpilot created.
- **Dev-env integration**:
  - `<gameDir>/blockpilot/endpoint.json` signals that the endpoint is ready.
  - JVM properties `blockpilot.autojoin` / `blockpilot.autocreate` join (and if
    needed create) a test world on launch.
  - A dependency-free CLI (`python3 tools/blockpilot.py --game-dir run
    --wait-ready 180 --wait-world "status"`, standard library only) lets an
    agent in a shell drive a game it just launched, without an MCP reconnect.
  - Mod jars are published to mavenLocal so other mods' dev environments can add
    Blockpilot as a runtime dependency.
- **Server-side extension handlers**: extension commands can declare that they
  run on the integrated server thread (queries and actions), so integrations
  like `mymod:build` touch the server world safely.
- **Safety gate**: everything above that reads or changes server state works
  only in a singleplayer world that allows commands (cheats on). Elsewhere it
  fails with `err no_cheats` or `err no_server`. Agents playing a normal
  survival world can't use it to cheat.

Out of scope: Sable compat commands (world↔plot coordinates, sub-level info), a
dedicated-server half with fake players, and remote servers.

## Capabilities

### New Capabilities
- `server-bridge`: Running commands on the integrated server with captured
  results, server-side NBT/block/entity queries, and the cheats/no-server gate.
  Its paused-game requirement comes from `add-sable-support`, which
  implemented it.
- `tick-control`: Freezing, stepping and rate-setting server ticks, plus
  server-clock sampling and condition evaluation.
- `log-stream`: Reading, marking, filtering and subscribing to the game log,
  with condensed stack traces.
- `test-worlds`: Creating disposable test worlds, snapshotting and restoring
  them, and deleting Blockpilot-created worlds.
- `dev-integration`: Launch-time auto-join, the endpoint readiness file, the
  standalone CLI client, and publishing jars for use in other mods' dev
  environments.

### Modified Capabilities
- `extension-api`: adds server-thread handlers for extension queries and
  actions. Added requirements only. The capability is introduced by
  `add-agent-control-mod`.

## Impact

- **Code**: new `dev.blockpilot.game.server` (bridge, tick, NBT, snapshots), a
  log4j appender in `dev.blockpilot.game.log`, and additions to the session
  commands. The CLI is `tools/blockpilot.py` (Python 3, standard library
  only). Version-sensitive spots: world creation, the tick manager API, and
  NBT ↔ JSON conversion.
- **Dependencies**: none new. Log4j, Gson and Brigadier ship with Minecraft.
- **Filesystem**: `<gameDir>/blockpilot/endpoint.json`,
  `<gameDir>/blockpilot/snapshots/`, and a marker file in Blockpilot-created
  saves.
- **Mod metadata**: unchanged, still client-only. The integrated server is
  reached from the client side of the same JVM.

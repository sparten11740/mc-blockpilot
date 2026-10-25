# Tasks

## 1. Server plumbing (server-bridge foundation)

- [x] 1.1 Extend the platform seam with integrated-server hooks (server tick end, server stopping) and client hooks (screen init, client stopping) on both loaders (design D15; the planned server-started hook had no user and was dropped); verified on NeoForge and Fabric: server commands and `tick step` run from the tick hook, a freeze is undone on leave (server stopping), auto-join runs from screen init, and a clean quit removes `endpoint.json` (client stopping)
- [x] 1.2 Add the `needsServer()` spec flag and the dispatcher gate (`err no_server` / `err no_cheats`, from the local player's permission level on the integrated server); the gate reads Minecraft client state, so instead of a fake-server unit test it was verified in game: a survival world without cheats (`no_cheats` for `nbt`, `snapshot`, `server`, `tick`, `side=server`), a cheats world, and the same survival world opened to LAN with cheats (allowed); the engine side (`no_server` stops a server command before its handler) is unit-tested in `ExtensionApiTest`
- [x] 1.3 Run server commands through queue affinity and `side=server` reads through `Sides.read` (no `ServerCall` class, see design D2), gate a run of queries at once, and list `needs singleplayer world with cheats on` in `help <command>`; verified: 1,000 `nbt` queries in one script answer in under 0.1 s, in one server tick, while the client keeps 112 fps with a 1% low of 102

## 2. Server bridge commands (server-bridge)

- [x] 2.1 Implement `server [as=player] [settle=<Nt>] <command>` with a capturing `CommandSource` and `CommandResultCallback` (`result`, `success`, feedback rows, `err command_failed` with the message rather than Brigadier's pointer line, results after `settle` ticks); verified the probe-a-block, failing-command, mod-command-output (`neoforge tps`, four lines) and client-sees-the-change scenarios
- [x] 2.2 Make `block`, `blocks` and `find` take a `Level` with a chunk-source loaded check (no `LevelView`, see design D4) and add `side=server` (server thread, `uuid` on entity rows, `err unloaded` without loading chunks); verified the forceloaded-chunk and unloaded-chunk scenarios (no region file created for x=90000), and the smoke test passes unchanged on both loaders
- [x] 2.3 Implement `nbt <pos|e<id>|uuid> [path=…]` (block entity full metadata, entity `saveWithoutId` in common code until a target needs `ValueOutput`, `NbtPathArgument`, SNBT text / JSON `value`, 8 KB cap with `truncated <size>`); verified the entity-position and large-block-entity (40,143 characters, value cut to 8,192) scenarios in both formats
- [x] 2.4 Verify the consistent-within-a-tick scenario: eight armor stands summoned mid-air in one command all report the same height in one `find … side=server` (37.3, while the client still showed 38.9)

## 3. Tick control and server clock (tick-control)

- [x] 3.1 Implement `tick freeze|unfreeze|rate <tps>|query` on `ServerTickRateManager`, recording Blockpilot's own changes and resetting them in the server-stopping hook; verified the freeze-and-query and leave-while-frozen scenarios on both loaders, and `tick rate 40`
- [x] 3.2 Implement `tick step <n>` with completion on the server tick-end hook, counted in game time (`err not_frozen` when running); verified the step-twenty-ticks scenario (tick 15906 to exactly 15926)
- [x] 3.3 Add thread affinity to the one action queue (client vs server ticks, locked queue head, server-affinity actions cancelled on the server thread; no separate scheduler, see design D7); verified with unit tests that ordering across affinities is FIFO and that cancel runs on the server thread
- [x] 3.4 Implement `sample <query> every <Nt> x<n> [clock=client|server]` (`every` separates the query; rows with `tick` + query fields; `err syntax` for client queries on the server clock) and the server clock for `wait … until` (reports the matching tick; on a frozen server it times out normally, see design D7); verified the sample-a-body's-height and wait-for-rest (`OnGround`) scenarios, and that two runs from the same snapshot report identical heights at identical tick offsets

## 4. Log stream (log-stream)

- [x] 4.1 Add the Log4j2 appender at mod construction (root logger, INFO+, 5,000-entry ring buffer, re-attached when a reconfiguration drops it, checked every 10 s instead of a startup self-test; Blockpilot logs nothing per request, see design D8); verified on both loaders that startup entries are captured, and that a server-thread ERROR (an invalid item in `summon`) is captured with `[Server thread]` and the `ItemStack` logger
- [x] 4.2 Implement condensed stack traces (three non-JDK/non-logging frames, `caused by` chain) and `log detail <seq>`; verified with unit tests on a synthetic nested exception and on a real Log4j ERROR followed through the appender to the full trace `log detail` returns
- [x] 4.3 Implement `log mark` and `log [level] [since] [grep] [logger] [limit]` (newest `limit` matches oldest first, `+N more`, `count=0` when nothing matches) in text and JSON; verified the errors-from-one-experiment, nothing-logged and startup-warnings scenarios
- [x] 4.4 Implement `sub log <level>|off` with the 20/s window and `! log_dropped n` with the next reply; verified a WARN pushed into an action's reply, and a burst of about 500 lines (`! log_dropped 492`)

## 5. Test worlds and snapshots (test-worlds)

- [x] 5.1 Implement `create world` (creation call in common code until a target differs, flat/void/normal presets, `peaceful|easy|hard|difficulty=…`, `mode=`, `seed=`, `rule:` applied via `server gamerule` after join, `blockpilot-test.json` marker, `err exists`); verified the flat-peaceful and name-taken scenarios, and that the player stands at y = -60
- [x] 5.2 Show the `test` flag in `worlds` from the marker, and list the loaded world (which vanilla's listing skips) with `loaded`; verified the worlds-listing scenario
- [x] 5.3 Implement name validation for worlds and snapshots (allowed characters, no leading `.`, resolved path stays inside its parent); the check reads the game directory, so it was verified in game instead of with unit tests: `../x`, `..\\x` and `/tmp/x` are refused for `create world`, `snapshot save` and `delete world`
- [ ] 5.4 Implement `snapshot save|restore|list|delete` (leave → copy without `session.lock` → rejoin; restore via a temp rename with rollback on failure; 300 s default timeout; size and duration in results; cheats gate); verified without Create: reset-between-runs (block, inventory, position, entities and the scoreboard's `SavedData` restored), unknown-snapshot and survival-world scenarios. Still open: the same with Create installed (block entities and a moving contraption)
- [x] 5.5 Implement `delete world <folder>` (marker required, not loaded, removes its snapshots too); verified the delete-a-user's-world (a world made through the vanilla screen) and delete-a-test-world scenarios

## 6. Dev-environment integration (dev-integration)

- [x] 6.1 Write `<gameDir>/blockpilot/endpoint.json` atomically after bind and remove it on client stopping; verified the agent-waits-for-launch scenario, that a clean quit removes the file on both loaders, and that a killed process leaves a file whose pid is dead (which the CLI skips)
- [x] 6.2 Implement `blockpilot.autojoin` / `blockpilot.autocreate` from the first title or onboarding screen; verified the first-launch-in-a-fresh-run-directory (no `options.txt`) and missing-world scenarios on both loaders
- [x] 6.3 Ship the CLI as `tools/blockpilot.py` (Python 3 standard library; `--game-dir`, `--json`, `--wait-ready`, `--wait-world`, exit codes 0/1/2, stale-pid check; replaces the planned Java jar, see design D12); verified the one-shot, JSON, launch-then-act and launch-with-auto-join scenarios against `runClient` on both loaders
- [x] 6.4 Add `maven-publish` for both node jars (`dev.blockpilot:blockpilot-<loader>:<version>+<minecraft>`, remapped jar on Fabric) and the extension API (`dev.blockpilot:blockpilot-api`); verified `./gradlew publishToMavenLocal` puts all three in `~/.m2/repository/dev/blockpilot/`
- [ ] 6.5 Document dev-env use in the README (ModDevGradle `localRuntime` + `systemProperty`, Loom `modLocalRuntime` + `vmArg`, auto-join, CLI usage, the cheats gate, snapshot caveats about static mod state); written. Still open: verify the NeoForge dev-env scenario by following the README in a fresh NeoForge 1.21.1 MDK project

## 7. Server-thread extension handlers (extension-api)

- [x] 7.1 Add `CommandSpec.onServer()` to the API (in the unreleased 1.0.0, see design D14), run server queries at the server tick end and server actions with server affinity, and apply the gate; verified the server-side-build, no-integrated-server and server-clock-sampling scenarios with the test-only sample extension in `ExtensionApiTest`
- [x] 7.2 Extend the README extension section with a server-thread action example (and events and the versioning rule); verified: a copy of the README snippets compiles and runs in the test source set (`ReadmeSnippets`, exercised by `ExtensionApiTest`)

## 8. Integration checks

- [ ] 8.1 Re-run one Aerolarge RCON experiment through Blockpilot in the Aerolarge dev env (runtime dependency from mavenLocal, auto-join a flat test world, `server aerolarge build …`, a real Physics Assembler drag instead of `sable assemble area`, `sample nbt … clock=server` instead of `sleep` polling, `log since=<mark> level=warn`, `snapshot restore` between runs); verify the resting-height numbers match the RCON results, and record the steps in the README as a worked example
- [x] 8.2 Run the full smoke test from the first change plus the new commands on both 1.21.1 jars in text and JSON mode; verified 28/28 on both, and `--shapes` gives the same JSON structure on NeoForge and Fabric (two lines differ only by world state)
- [x] 8.3 Run `openspec validate add-test-harness --strict`; verified it passes

## Workflow follow-up

- Apply and archive this change only after `add-agent-control-mod` is archived, so the `extension-api` additions land on the existing spec.
- Follow-ups: Sable compat (`sable:info`, world↔plot conversion), a dedicated-server half with fake players, `26.1` targets.

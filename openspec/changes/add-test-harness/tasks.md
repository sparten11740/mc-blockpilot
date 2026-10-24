# Tasks

## 1. Server plumbing (server-bridge foundation)

- [ ] 1.1 Extend the platform seam with integrated-server hooks (server started, server tick end, server stopping) and client hooks (screen init, client stopping) on both loaders (design D15); verify each hook fires in `runClient` on NeoForge and Fabric by joining and leaving a singleplayer world, with log lines
- [ ] 1.2 Add the `requiresServer` registry flag and the dispatcher gate (`err no_server` / `err no_cheats`, from the local player's permission level on the integrated server); verify with unit tests on a fake server view, and in game on a survival world, a cheats world, and a world opened to LAN with cheats
- [ ] 1.3 Implement `ServerCall` (submit to the server thread, complete the session future, never block the network thread) and add `help` text listing which commands need the server; verify with a test that runs 1,000 server queries back to back without stalling the client frame rate

## 2. Server bridge commands (server-bridge)

- [ ] 2.1 Implement `server <command> [as=player]` with a capturing `CommandSource` and `CommandResultCallback` (`result`, `success`, feedback rows, `err command_failed`); verify the probe-a-block, failing-command and mod-command-output scenarios
- [ ] 2.2 Rewrite `block`, `blocks` and `find` against a `LevelView` (level, loaded-chunk check, entity lookup) and add `side=server` (server thread, `uuid` on entity rows, `err unloaded` without loading chunks); verify the forceloaded-chunk and unloaded-chunk scenarios, and that client-side results are unchanged (rerun the first change's golden tests)
- [ ] 2.3 Implement `nbt <pos|e<id>|uuid> [path=…]` (block entity full metadata, per-version entity save in `src/1.21.1/`, `NbtPathArgument`, SNBT text / JSON `value`, 8 KB cap with `truncated <size>`); verify the entity-position and large-block-entity scenarios in both formats
- [ ] 2.4 Verify the consistent-within-a-tick scenario with a test world full of moving armor stands, checking that all positions from one `find … side=server` call match a single tick

## 3. Tick control and server clock (tick-control)

- [ ] 3.1 Implement `tick freeze|unfreeze|rate <tps>|query` on `ServerTickRateManager`, recording Blockpilot's own changes and resetting them in the server-stopping hook; verify the freeze-and-query and leave-while-frozen scenarios
- [ ] 3.2 Implement `tick step <n>` with completion on the server tick-end hook (`err not_frozen` when running); verify the step-twenty-ticks scenario, including the exact reported tick number
- [ ] 3.3 Implement the `ServerScheduler` and thread affinity in the action queue (client vs server ticks, locked queue head) (D7); verify with unit tests on two fake tick sources that ordering across affinities is FIFO
- [ ] 3.4 Implement `sample <query> every <Nt> x<n> [clock=client|server]` (`every` keyword separates the query from sample's arguments; rows with `tick` + query fields, `err syntax` for client queries on the server clock) and `clock=server` for `wait … until`; verify the sample-a-body's-height and wait-for-rest scenarios, and that two runs from the same snapshot report identical tick offsets

## 4. Log stream (log-stream)

- [ ] 4.1 Add the Log4j2 appender at mod construction (root logger, INFO+, 5,000-entry ring buffer, ignored own logger, re-attach on reconfiguration, startup self-test); verify the self-test passes on both loaders and that a server-thread ERROR is captured with thread and logger
- [ ] 4.2 Implement condensed stack traces (three non-JDK/non-logging frames, `caused by` chain) and `log detail <seq>`; verify with a unit test on a synthetic nested exception
- [ ] 4.3 Implement `log mark` and `log [level] [since] [grep] [logger] [limit]` (oldest first, `+N more`, `ok none`) in text and JSON; verify the errors-from-one-experiment, nothing-logged and startup-warnings scenarios
- [ ] 4.4 Implement `sub log <level>|off` with the 20/s token bucket and `! log dropped n`; verify the error-pushed-during-an-action scenario and the cap with a burst of 500 log lines

## 5. Test worlds and snapshots (test-worlds)

- [ ] 5.1 Implement `create world` (per-version creation call in `src/1.21.1/`, flat/void/normal presets, difficulty, `mode=`, `seed=`, `rule:` applied via `server gamerule` after join, `blockpilot-test.json` marker, `err exists`); verify the flat-peaceful and name-taken scenarios, and that the flat surface is at y = -60
- [ ] 5.2 Show the `test` flag in `worlds` from the marker; verify the worlds-listing scenario
- [ ] 5.3 Implement name validation for worlds and snapshots (allowed characters, no `.`/`..`, resolved path stays inside its parent); verify with unit tests covering traversal attempts (`../x`, absolute paths, `..\\x`)
- [ ] 5.4 Implement `snapshot save|restore|list|delete` (leave → copy without `session.lock` → rejoin; restore via a temp rename with rollback on failure; 300 s default timeout; size and duration in results; cheats gate); verify the reset-between-runs scenario with Create installed (block entities and a moving contraption restored), and the unknown-snapshot and survival-world scenarios
- [ ] 5.5 Implement `delete world <folder>` (marker required, not loaded, removes its snapshots too); verify the delete-a-user's-world and delete-a-test-world scenarios

## 6. Dev-environment integration (dev-integration)

- [ ] 6.1 Write `<gameDir>/blockpilot/endpoint.json` atomically after bind and remove it on client stopping; verify the agent-waits-for-launch scenario and that a killed process leaves a file whose pid is dead
- [ ] 6.2 Implement `blockpilot.autojoin` / `blockpilot.autocreate` from the first title or onboarding screen; verify the first-launch-in-a-fresh-run-directory and missing-world scenarios on both loaders
- [ ] 6.3 Create the `blockpilot-cli` project (JDK only, Java 17 bytecode, executable jar; `--game-dir`, `--json`, `--wait-ready`, `--wait-world`, exit codes 0/1/2); verify the one-shot, JSON, launch-then-act and launch-with-auto-join scenarios against `runClient`
- [ ] 6.4 Add `maven-publish` for both node jars (`dev.blockpilot:blockpilot-<loader>:<version>+<minecraft>`, remapped jar on Fabric) and the CLI; verify `./gradlew publishToMavenLocal` puts all three in `~/.m2/repository/dev/blockpilot/`
- [ ] 6.5 Document dev-env use in the README (ModDevGradle `localRuntime` + `jvmArgument`, Loom `modLocalRuntime` + `vmArg`, auto-join, CLI usage, the cheats gate, snapshot caveats about static mod state); verify the NeoForge dev-env scenario by following the README in a fresh NeoForge 1.21.1 MDK project

## 7. Server-thread extension handlers (extension-api)

- [ ] 7.1 Add `CommandSpec.onServer()` to the API (minor version bump), route server queries through `ServerCall` and server actions through server affinity, and apply the gate; verify the server-side-build, no-integrated-server and server-clock-sampling scenarios with the test-only sample extension
- [ ] 7.2 Extend the README extension section with a server-thread query and action example; verify the example compiles in the sample-extension source set

## 8. Integration checks

- [ ] 8.1 Re-run one Aerolarge RCON experiment through Blockpilot in the Aerolarge dev env (runtime dependency from mavenLocal, auto-join a flat test world, `server aerolarge build …`, a real Physics Assembler drag instead of `sable assemble area`, `sample nbt … clock=server` instead of `sleep` polling, `log since=<mark> level=warn`, `snapshot restore` between runs); verify the resting-height numbers match the RCON results, and record the steps in the README as a worked example
- [ ] 8.2 Run the full smoke test from the first change plus the new commands on both 1.21.1 jars in text and JSON mode; verify identical result structure on NeoForge and Fabric
- [ ] 8.3 Run `openspec validate add-test-harness --strict`; verify it passes

## Workflow follow-up

- Apply and archive this change only after `add-agent-control-mod` is archived, so the `extension-api` additions land on the existing spec.
- Follow-ups: Sable compat (`sable:info`, world↔plot conversion), a dedicated-server half with fake players, `26.1` targets.

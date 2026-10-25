# Tasks

## 1. Build scaffolding (multi-target-build)

- [x] 1.1 Add the Gradle wrapper (9.8.x), `gradle/gradle-daemon-jvm.properties` (JDK 25), root `gradle.properties`, and root `build.gradle.kts` pinning `net.neoforged.moddev`, `net.fabricmc.fabric-loom-remap`, `net.fabricmc.fabric-loom` and `dev.kikugie.loom-back-compat` as in `../ultrasplosive`; verify `./gradlew help` succeeds
- [x] 1.2 Add `settings.gradle.kts` with Stonecutter 0.9.x and the `blockpilot` tree targeting `1.21.1` × `neoforge`, `fabric` (`vcsVersion = "1.21.1-neoforge"`); verify `./gradlew projects` lists both nodes
- [x] 1.3 Add `blockpilot/stonecutter.gradle.kts` (loader constants, empty rename list with a comment explaining how to add rules), `blockpilot/gradle.properties` (`mod_id=blockpilot`, `mod_group_id=dev.blockpilot`, `mod_version=0.1.0-alpha.1`) and `versions/1.21.1-{neoforge,fabric}/gradle.properties` (Minecraft, Java 21, NeoForge, Fabric Loader/API, Parchment versions); verify the IDE sync / `./gradlew :blockpilot:1.21.1-fabric:dependencies` resolves
- [x] 1.4 Port `build.neoforge.gradle.kts` and `build.fabric.gradle.kts` from Ultrasplosive (exclude the other loader's `platform.*`, per-version source dirs, metadata generation, jar output to root `build/libs`), dropping the Supplementaries logic and adding a JUnit 5 `test` task; verify `./gradlew build` produces `blockpilot-0.1.0-alpha.1+1.21.1-{neoforge,fabric}.jar`
- [x] 1.5 Add `src/main/templates/fabric.mod.json` (`environment: client`) and `META-INF/neoforge.mods.toml` (client-side dist), plus `blockpilot.mixins.json` / per-loader mixin configs; verify both jars' metadata declare the 1.21.1 range and client side only
- [x] 1.6 Add the `ClientPlatform` seam (config dir, key-mapping registration, end-of-client-tick hook, HUD render hook, chat-received hook, client command registration) with `platform.neoforge` / `platform.fabric` implementations and entry points that log `Blockpilot <version> on <loader>`; verify `runClient` on both nodes reaches the title screen with that log line
- [x] 1.7 Write `README.md` covering the layout, how to build, how to switch the active Stonecutter node, and the steps to add a target (26.1 as the worked example); verify the build commands in it run as written

## 2. Protocol core: command language (command-language)

- [x] 2.1 Implement the tokenizer and script parser in `dev.blockpilot.protocol` (`;`/newline separation, `\;` escape, trailing free-text arguments, position tokens `x,y,z` / `~dx,dy,dz`, `e<id>` refs, `#tag`, `x<n>`, `<n>t`/`<n>s` durations, `name=value` options, `<namespace>:<name>` command names); verify with JUnit tests covering each token form and `err syntax` cases
- [x] 2.2 Implement the structured `Result` model (ok/err/skip, error code + argument, ordered typed fields, keyed rows) and the command registry (name, argument grammar, query/action kind, declared result fields with text rendering, help text, example, per-target support predicate); verify with tests that every registered command has an example that parses
- [x] 2.3 Generate `help`, `help <topic>` (including result fields), `help errors` and the `run` cheat sheet from the registry; verify with golden tests, and that the cheat sheet plus tool schemas stay under 400 tokens (count with a tokenizer estimate in the test)
- [x] 2.4 Implement the text formatter (`ok`/`err <code>`/`skip`, indented payloads, fail-fast skip filling, `#id` tagging, `.` terminator, namespace stripping, default omission, compact field syntax) and the JSON formatter (same fields, snake_case, `results`/`events`/`id`), plus `fmt text|json` per session; verify with golden tests that render the same `Result` fixtures in both formats
- [x] 2.5 Implement the keyed-row diff engine (added/removed/changed, `same`, `full`; text `+`/`-`/`~`, JSON arrays) and the per-session event ring buffer (cap 50, `! dropped n`); verify with unit tests in both formats
- [x] 2.6 Implement the `wait <query> until <predicate> [and …]` parser and evaluator (operators `== != < <= > >=` and `ok`, `count`, dotted field paths, `stable=`/`every=`/`timeout=`, rejecting action queries, `err timeout` with last values); verify with unit tests driven by a fake tick source and canned `Result`s
- [x] 2.7 Add a JUnit architecture test: nothing in `api` or `protocol` imports `net.minecraft`, and `api` imports nothing from `protocol`, `game` or `platform`; verify it fails on a deliberately bad import and passes after removing it

## 3. Endpoint (agent-endpoint)

- [x] 3.1 Check whether `io.netty:netty-codec-http` is on the runtime classpath of both 1.21.1 targets (game library list / dev runtime). Record the result in design.md D3 and pick `HttpServerCodec` or the in-house HTTP/1.1 decoder; verify by loading the class in `runClient` on both loaders
- [x] 3.2 Implement config + token handling (`config/blockpilot/config.json` with port/bind/timeouts/caps/notable list, `token` file with owner-only permissions, constant-time compare, the token file path logged and recorded in endpoint.json instead of the token itself; no clipboard command, see design D11); verify with unit tests for load/default/regenerate and a manual run
- [x] 3.3 Implement the Netty server with first-bytes protocol sniffing, loopback binding, port-in-use handling (log + one-time in-game notice); verify with a test that opens both an HTTP and a line connection on one port
- [x] 3.4 Implement the line protocol (`auth` handshake reply including the API version, `#id` pipelining in order, `.` framing in text mode, one JSON line per reply in JSON mode, `sub on|off` pushed events in both modes, disconnect cleanup); verify with an in-process test client in both modes and with `nc 127.0.0.1 25599` against `runClient`
- [ ] 3.5 Implement the MCP Streamable HTTP handler (initialize with version negotiation, initialized, ping, tools/list, tools/call; session ids, 401/403/404/405 cases, Origin check, `isError` tool results, image content for `screenshot`); verify with JUnit JSON-RPC tests and by connecting Claude Code via `claude mcp add --transport http blockpilot http://127.0.0.1:25599/mcp --header "Authorization: Bearer <token>"`
- [x] 3.6 Implement the session model, client-thread query execution, and the shared FIFO action queue with the tick-driven `Action` state machine, timeouts, `stop`, and cancel-on-session-close; verify with unit tests using a fake tick source, plus the "two sessions issue actions" scenario manually
- [x] 3.7 Measure `status` round-trip latency over the line protocol in a world; verify it is under 100 ms, and record the number in the README

## 4. Extension API (extension-api)

- [x] 4.1 Create `dev.blockpilot.api` (`Blockpilot.register`, `CommandSpec` builder with typed arguments and result fields, query handler, `ExtensionAction` start/tick/cancel, `Blockpilot.events(ns).emit`, API version constant) and the static registry that the command table is built from when mod loading completes, before the listener starts (add a load-complete hook to `ClientPlatform`); verify with unit tests that registering before and after Blockpilot's own init both work, and that registering after the table is built is rejected with a log line
- [x] 4.2 Wire extensions into the registry: `<namespace>:<name>` resolution, no shadowing of built-ins, duplicate detection (first wins, log names both mods), `help` namespaces/commands/fields, unchanged MCP tool list; verify with unit tests for each spec scenario
- [x] 4.3 Run extension handlers through the same paths as built-ins (scripts, fail-fast, action queue, `stop` → cancel hook, timeouts, diffs, `fmt json`, `wait … until`), with fault isolation (`err ext_failed`, stack trace logged, session survives) and extension events (`! <ns>:<event>`); verify with a test-only sample extension covering each scenario
- [x] 4.4 Add the `blockpilot-api` project (plain `java-library` over `dev/blockpilot/api/**`, `maven-publish` to mavenLocal as `dev.blockpilot:blockpilot-api`); verify `./gradlew :blockpilot-api:publishToMavenLocal` produces a jar containing only `dev/blockpilot/api` classes
- [x] 4.5 Document the extension API in the README (soft-dependency pattern with an `isLoaded("blockpilot")` guard and a separate integration class, an example query and action, events, versioning policy); verified: a copy of the README snippets compiles and runs in the test source set (`ReadmeSnippets`, exercised by `ExtensionApiTest`)

## 5. Perception (game-perception)

- [x] 5.1 Implement `status` (all fields declared in the registry, default omission, `menu` outside a world); verify against the spec scenarios in `runClient` on both loaders, in text and JSON mode
- [ ] 5.2 Implement `look [radius]` with section-palette pruning, block-entity listing, the configurable notable list, entity listing, caps with `+N more`, and keyed rows for diffs; verify the cave scenario, a Create block-entity being listed on NeoForge, and scan time < 1 ms at radius 16 (log timing)
- [x] 5.3 Implement `find` (blocks by id/tag/pattern, entities by type, nearest-first, `ok none`, `count` field); verify the spec scenarios
- [x] 5.4 Implement `block <pos>` (`id`, `props`, `be`); verify the chest and plain-block scenarios and `wait block <pos> until id==air` after breaking it
- [x] 5.5 Implement `blocks <pos1> <pos2>` (palette, Y layers, RLE, non-default state properties, `err too_large 4096`); verify with a golden test on a known built structure
- [x] 5.6 Implement `inv` / `inv detail` with keyed diff rows; verify the compact-inventory scenario and `ok same`
- [x] 5.7 Implement `screen` (container slots, vanilla widget kinds with bounds and state, unknown widgets with bounds, GUI scale, size, cursor); verify on a crafting table, the options screen (slider) and one modded screen; verified: a crafting table (46 slots, filled slots and the recipe-book button with bounds), the options screen (`slider "FOV: 73"` with bounds), and NeoForge's mod list as the modded screen (its own `ModListWidget` listed by class with bounds, next to vanilla text fields and buttons)
- [x] 5.8 Implement `screenshot [width]` (render-thread capture, NativeImage downscale, PNG, MCP image content / file path on line protocol); verify both transports return a correctly sized image
- [x] 5.9 Implement event detection (chat via platform hook; hurt, death, got/lost items, screen open/close by tick polling; action finished/interrupted); verify the "damage while waiting" scenario
- [x] 5.10 Add a test or debug assertion that perception commands don't change rotation, position or selected slot and send no packets; verify it passes across all perception commands; done as a smoke-test step: every perception command runs across a few dozen ticks inside a perf capture, and status, inventory and selected slot stay the same and the client sends only position reminders, keep-alives and chunk batch acks; passes on both loaders

## 6. Raw input layer (player-control: raw input, focus, override)

- [x] 6.1 Add 1.21.1 invoker mixins for `MouseHandler` (`onPress`, `onMove`, `onScroll`) and `KeyboardHandler` (`keyPress`, `charTyped`) in `src/1.21.1/`, plus the `InputState` held-input ledger and `releaseAll`; verify `mouse down right; wait 40t; mouse up right` holds right-click (e.g. drawing a bow)
- [x] 6.2 Implement the virtual cursor and `mouse move`/`drag` (in-world raw pixels through sensitivity, on-screen GUI-scaled units, spread over N ticks), `cursor`, `click`, `scroll`, `key down|up|press` (key names + key-mapping ids resolved to bound keys), and `type`; verify the slider-drag scenario on the options screen and a modded key mapping press
- [ ] 6.3 Mixin `InputConstants.isKeyDown` and mouse-button polling so they consult `InputState`; verify a Create Simulated physics assembler drag works on 1.21.1 NeoForge and/or Fabric (whichever it ships for), and record the result
- [x] 6.4 Implement unfocused-window support (window-active / pause-on-lost-focus / mouse-grab overrides only while a session is connected, no OS cursor capture while unfocused); verify the "window in background" scenario, and that normal behavior returns after disconnect
- [ ] 6.5 Implement human-override detection (callbacks without the `injecting` flag), the agent-control toggle key mapping, `err interrupted` / `err disabled`, and release-on-session-close; verify the "user grabs control" and "session dropped mid-hold" scenarios
- [x] 6.6 Implement the HUD indicator (connected / acting with action name / disabled) through the platform HUD hook; verify it shows on both loaders

## 7. High-level actions (player-control)

- [x] 7.1 Implement `move`, `jump`, `sneak`, `sprint`, `face`, `turn` (key-mapping driven, rotation smoothed over ticks); verify the face-a-block scenario
- [x] 7.2 Implement the A* pathfinder (walk, diagonal, jump 1, drop ≤ 3, swim, climb; per-tick node budget) as code without game dependencies behind a block-access interface; verify with unit tests on synthetic grids (unreachable, drop, ladder)
- [x] 7.3 Implement path following for `goto <pos|e<id>> [near=r]` (look-ahead steering, re-plan on deviation, `stuck` after 3 s, timeouts); verify the arrival, no-path and follow-entity scenarios in game
- [ ] 7.4 Implement `mine <pos>`, `mine <block|#tag> x<n>` (best-tool selection, walk into reach), `place <item> <pos> [face]`, `use [pos|e<id>]`; verify the mine-several-logs and place-without-item scenarios on a vanilla server and singleplayer
- [x] 7.5 Implement `attack` with cooldown timing and follow-within-16; verify the attack-until-dead scenario
- [x] 7.6 Implement `select`, `swap`, `offhand`, `equip`, `drop`; verify the select-from-main-inventory scenario
- [x] 7.7 Implement `craft <item> [x<n>]` via `handlePlaceRecipe` on 1.21.1 (2x2 or nearby/open table, `no_recipe`/`missing`/`need_table`), in a per-version file; verify the craft-planks scenario
- [x] 7.8 Implement `open`, `take`, `put`, `click s<index> [mode]`, `close`; verify the take-from-chest scenario
- [x] 7.9 Implement `chat`, `cmd`, `wait <Nt|Ns|idle>`, `wait <query> until …` (hooking the 2.6 evaluator into the action queue), `stop`, `release`; verify the stop-interrupts-goto scenario across two sessions and the wait-for-block-to-disappear scenario

## 8. Session control (game-session)

- [x] 8.1 Implement `worlds` and `servers` listing from any screen; verify against a dev instance with two saves and one server entry; verified with several saves (the loaded one flagged) and one server entry added through the vanilla Add Server screen, which `servers` lists as `0 "Blockpilot Test" 127.0.0.1:25570`; `join server 0` with nothing listening fails with `err join_failed`
- [x] 8.2 Implement `join world <folder>`, `join server <index|address>` (leave first if in a game, result once the player can act, `err join_failed <reason>`) and `leave`; verify the join-save, server-unreachable and leave scenarios
- [x] 8.3 Implement session events (`! disconnected`, `! kicked`, `! died <cause>`) and `respawn`, and `err not_in_world` gating for world commands; verify the death/respawn and mine-from-title-screen scenarios

## 9. Integration checks

- [ ] 9.1 Run the same scripted smoke test (`tools/smoke.sh` over the line protocol: auth, status, look, block, inv, goto, mine, craft, screen, screenshot, wait … until, stop) against both 1.21.1 jars in a production-like instance; verify identical reply structure on NeoForge and Fabric
- [ ] 9.2 Run the smoke test again in JSON mode and parse every reply with a JSON parser; verify each reply is one valid JSON line, with the same fields as the text run
- [ ] 9.3 Install a separately built sample mod that integrates via `blockpilot-api` from mavenLocal into both 1.21.1 instances; verify its commands work over text and JSON, and that the sample mod still loads with Blockpilot removed
- [ ] 9.4 Run an end-to-end MCP session from Claude Code (join a world, gather wood, craft planks and a crafting table, place it); verify it finishes with only `run`/`help`/`screenshot` calls, and record total tokens used in the README as a baseline
- [x] 9.5 Run `openspec validate add-agent-control-mod --strict`; verify it passes

## 10. Script checks (Aerolarge feedback, command-language)

- [x] 10.1 Let predicates compare two fields (`$<field>` on the right-hand side, also in `wait … until`) and report the referenced field among the observed values; verify with `ConditionTest`
- [x] 10.2 Add `assert <query> <predicates>` and `if <query> <predicates> then <command>`, evaluated by the script runner with the inner query's thread and gate (`err assert_failed`, `ok false`, the `then` command gated and run in place, `?` soft forms, rejected inside `wait` and `sample`); verify with `EngineTest` cases for each, including a server-side query and an `if` whose command is an action
- [ ] 10.3 Document `assert`/`if` in help, the cheat sheet and the README; verify in game that `assert block ~0,-1,0 id==grass_block` passes on grass and fails on stone, and that `if … then place …` places only when the predicate holds

## Workflow follow-up

- Archive the change once 1.21.1 NeoForge and Fabric are verified.
- Follow-up changes: `add-test-harness` (integrated-server bridge, tick control, logs, test worlds, snapshots), Sable compat, a dedicated-server half, `26.1` targets (per D9), optional Baritone delegation.

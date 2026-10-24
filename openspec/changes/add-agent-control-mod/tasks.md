# Tasks

## 1. Build scaffolding (multi-target-build)

- [ ] 1.1 Add the Gradle wrapper (9.8.x), `gradle/gradle-daemon-jvm.properties` (JDK 25), root `gradle.properties`, and root `build.gradle.kts` pinning `net.neoforged.moddev`, `net.fabricmc.fabric-loom-remap`, `net.fabricmc.fabric-loom` and `dev.kikugie.loom-back-compat` as in `../ultrasplosive`; verify `./gradlew help` succeeds
- [ ] 1.2 Add `settings.gradle.kts` with Stonecutter 0.9.x and the `blockpilot` tree targeting `1.21.1` × `neoforge`, `fabric` (`vcsVersion = "1.21.1-neoforge"`); verify `./gradlew projects` lists both nodes
- [ ] 1.3 Add `blockpilot/stonecutter.gradle.kts` (loader constants, empty rename list with a comment explaining how to add rules), `blockpilot/gradle.properties` (`mod_id=blockpilot`, `mod_group_id=dev.blockpilot`, `mod_version=0.1.0-alpha.1`) and `versions/1.21.1-{neoforge,fabric}/gradle.properties` (Minecraft, Java 21, NeoForge, Fabric Loader/API, Parchment versions); verify the IDE sync / `./gradlew :blockpilot:1.21.1-fabric:dependencies` resolves
- [ ] 1.4 Port `build.neoforge.gradle.kts` and `build.fabric.gradle.kts` from Ultrasplosive (exclude the other loader's `platform.*`, per-version source dirs, metadata generation, jar output to root `build/libs`), dropping the Supplementaries logic and adding a JUnit 5 `test` task; verify `./gradlew build` produces `blockpilot-0.1.0-alpha.1+1.21.1-{neoforge,fabric}.jar`
- [ ] 1.5 Add `src/main/templates/fabric.mod.json` (`environment: client`) and `META-INF/neoforge.mods.toml` (client-side dist), plus `blockpilot.mixins.json` / per-loader mixin configs; verify both jars' metadata declare the 1.21.1 range and client side only
- [ ] 1.6 Add the `ClientPlatform` seam (config dir, key-mapping registration, end-of-client-tick hook, HUD render hook, chat-received hook, client command registration) with `platform.neoforge` / `platform.fabric` implementations and entry points that log `Blockpilot <version> on <loader>`; verify `runClient` on both nodes reaches the title screen with that log line
- [ ] 1.7 Write `README.md` covering the layout, how to build, how to switch the active Stonecutter node, and the steps to add a target (26.1 as the worked example); verify the build commands in it run as written

## 2. Protocol core: command language (command-language)

- [ ] 2.1 Implement the tokenizer and script parser in `dev.blockpilot.protocol` (`;`/newline separation, `\;` escape, trailing free-text arguments, position tokens `x,y,z` / `~dx,dy,dz`, `e<id>` refs, `#tag`, `x<n>`, `<n>t`/`<n>s` durations, `key=value` options); verify with JUnit tests covering each token form and `err syntax` cases
- [ ] 2.2 Implement the command registry (name, argument grammar, query/action kind, help text, example, per-target support predicate) and generate `help`, `help <topic>`, `help errors` and the `run` cheat sheet from it; verify with tests that every registered command has an example that parses
- [ ] 2.3 Implement the reply formatter (`ok`/`err <code>`/`skip`, indented payloads, fail-fast skip filling, `#id` tagging, `.` terminator for line framing) and identifier shortening (drop `minecraft:`); verify with golden-output tests
- [ ] 2.4 Implement the keyed-line diff engine (`+`/`-`/`~`, `same`, `full`) and the per-session event ring buffer (cap 50, `! dropped n`); verify with unit tests
- [ ] 2.5 Add a JUnit architecture test that fails if any class in `dev.blockpilot.protocol` imports `net.minecraft`; verify it fails on a deliberately bad import and passes after removing it

## 3. Endpoint (agent-endpoint)

- [ ] 3.1 Check whether `io.netty:netty-codec-http` is on the runtime classpath of both 1.21.1 targets (game library list / dev runtime). Record the result in design.md D3 and pick `HttpServerCodec` or the in-house HTTP/1.1 decoder; verify by loading the class in `runClient` on both loaders
- [ ] 3.2 Implement config + token handling (`config/blockpilot/config.json` with port/bind/timeouts/caps/notable list, `token` file with owner-only permissions, constant-time compare, first-start log line, `/blockpilot token` copying to clipboard); verify with unit tests for load/default/regenerate and a manual run
- [ ] 3.3 Implement the Netty server with first-bytes protocol sniffing, loopback binding, port-in-use handling (log + one-time in-game notice); verify with a test that opens both an HTTP and a line connection on one port
- [ ] 3.4 Implement the line protocol (`auth` handshake reply, `#id` pipelining in order, `.` framing, `sub on|off` pushed `!` events, disconnect cleanup); verify with an in-process test client and with `nc 127.0.0.1 25599` against `runClient`
- [ ] 3.5 Implement the MCP Streamable HTTP handler (initialize with version negotiation, initialized, ping, tools/list, tools/call; session ids, 401/403/404/405 cases, Origin check, `isError` tool results, image content for `screenshot`) and tool descriptions under 400 tokens; verify with JUnit JSON-RPC tests and by connecting Claude Code via `claude mcp add --transport http blockpilot http://127.0.0.1:25599/mcp --header "Authorization: Bearer <token>"`
- [ ] 3.6 Implement the session model, client-thread query execution, and the shared FIFO action queue with the tick-driven `Action` state machine, timeouts, `stop`, and cancel-on-session-close; verify with unit tests using a fake tick source, plus the "two sessions issue actions" scenario manually
- [ ] 3.7 Measure `status` round-trip latency over the line protocol in a world; verify it is under 100 ms, and record the number in the README

## 4. Perception (game-perception)

- [ ] 4.1 Implement `status` (all fields, default omission, `menu` outside a world); verify against the spec scenarios in `runClient` on both loaders
- [ ] 4.2 Implement `look [radius]` with section-palette pruning, block-entity listing, the configurable notable list, entity listing, caps with `+N more`, and keyed lines for diffs; verify the cave scenario, a Create block-entity being listed on NeoForge, and scan time < 1 ms at radius 16 (log timing)
- [ ] 4.3 Implement `find` (blocks by id/tag/pattern, entities by type, nearest-first, `ok none`); verify the spec scenarios
- [ ] 4.4 Implement `blocks <pos1> <pos2>` (palette, Y layers, RLE, non-default state properties, `err too_large 4096`); verify with a golden test on a known built structure
- [ ] 4.5 Implement `inv` / `inv detail` with keyed diff lines; verify the compact-inventory scenario and `ok same`
- [ ] 4.6 Implement `screen` (container slots, vanilla widget kinds with bounds and state, unknown widgets with bounds, GUI scale, size, cursor); verify on a crafting table, the options screen (slider) and one modded screen
- [ ] 4.7 Implement `screenshot [width]` (render-thread capture, NativeImage downscale, PNG, MCP image content / file path on line protocol); verify both transports return a correctly sized image
- [ ] 4.8 Implement event detection (chat via platform hook; hurt, death, got/lost items, screen open/close by tick polling; action finished/interrupted); verify the "damage while waiting" scenario
- [ ] 4.9 Add a test or debug assertion that perception commands don't change rotation, position or selected slot and send no packets; verify it passes across all perception commands

## 5. Raw input layer (player-control: raw input, focus, override)

- [ ] 5.1 Add 1.21.1 invoker mixins for `MouseHandler` (`onPress`, `onMove`, `onScroll`) and `KeyboardHandler` (`keyPress`, `charTyped`) in `src/1.21.1/`, plus the `InputState` held-input ledger and `releaseAll`; verify `mouse down right; wait 40t; mouse up right` holds right-click (e.g. drawing a bow)
- [ ] 5.2 Implement the virtual cursor and `mouse move`/`drag` (in-world relative camera via sensitivity, on-screen absolute cursor, spread over N ticks), `cursor`, `click`, `scroll`, `key down|up|press` (key names + key-mapping ids resolved to bound keys), and `type`; verify the slider-drag scenario on the options screen and a modded key mapping press
- [ ] 5.3 Mixin `InputConstants.isKeyDown` and mouse-button polling so they consult `InputState`; verify a Create Simulated physics assembler drag works on 1.21.1 NeoForge and/or Fabric (whichever it ships for), and record the result
- [ ] 5.4 Implement unfocused-window support (window-active / pause-on-lost-focus / mouse-grab overrides only while a session is connected, no OS cursor capture while unfocused); verify the "window in background" scenario, and that normal behavior returns after disconnect
- [ ] 5.5 Implement human-override detection (callbacks without the `injecting` flag), the agent-control toggle key mapping, `err interrupted` / `err disabled`, and release-on-session-close; verify the "user grabs control" and "session dropped mid-hold" scenarios
- [ ] 5.6 Implement the HUD indicator (connected / acting with action name / disabled) through the platform HUD hook; verify it shows on both loaders

## 6. High-level actions (player-control)

- [ ] 6.1 Implement `move`, `jump`, `sneak`, `sprint`, `face`, `turn` (key-mapping driven, rotation smoothed over ticks); verify the face-a-block scenario
- [ ] 6.2 Implement the A* pathfinder (walk, diagonal, jump 1, drop ≤ 3, swim, climb; per-tick node budget) as code without game dependencies behind a block-access interface; verify with unit tests on synthetic grids (unreachable, drop, ladder)
- [ ] 6.3 Implement path following for `goto <pos|e<id>> [near=r]` (look-ahead steering, re-plan on deviation, `stuck` after 3 s, timeouts); verify the arrival, no-path and follow-entity scenarios in game
- [ ] 6.4 Implement `mine <pos>`, `mine <block|#tag> x<n>` (best-tool selection, walk into reach), `place <item> <pos> [face]`, `use [pos|e<id>]`; verify the mine-several-logs and place-without-item scenarios on a vanilla server and singleplayer
- [ ] 6.5 Implement `attack` with cooldown timing and follow-within-16; verify the attack-until-dead scenario
- [ ] 6.6 Implement `select`, `swap`, `offhand`, `equip`, `drop`; verify the select-from-main-inventory scenario
- [ ] 6.7 Implement `craft <item> [x<n>]` via `handlePlaceRecipe` on 1.21.1 (2x2 or nearby/open table, `no_recipe`/`missing`/`need_table`), in a per-version file; verify the craft-planks scenario
- [ ] 6.8 Implement `open`, `take`, `put`, `click s<index> [mode]`, `close`; verify the take-from-chest scenario
- [ ] 6.9 Implement `chat`, `cmd`, `wait <Nt|Ns|idle>`, `stop`, `release`; verify the stop-interrupts-goto scenario across two sessions

## 7. Session control (game-session)

- [ ] 7.1 Implement `worlds` and `servers` listing from any screen; verify against a dev instance with two saves and one server entry
- [ ] 7.2 Implement `join world <folder>`, `join server <index|address>` (leave first if in a game, result once the player can act, `err join_failed <reason>`) and `leave`; verify the join-save, server-unreachable and leave scenarios
- [ ] 7.3 Implement session events (`! disconnected`, `! kicked`, `! died <cause>`) and `respawn`, and `err not_in_world` gating for world commands; verify the death/respawn and mine-from-title-screen scenarios

## 8. Integration checks

- [ ] 8.1 Run the same scripted smoke test (`tools/smoke.sh` over the line protocol: auth, status, look, inv, goto, mine, craft, screen, screenshot, stop) against both 1.21.1 jars in a production-like instance; verify identical reply structure on NeoForge and Fabric
- [ ] 8.2 Run an end-to-end MCP session from Claude Code (join a world, gather wood, craft planks and a crafting table, place it); verify it finishes with only `run`/`help`/`screenshot` calls, and record total tokens used in the README as a baseline
- [ ] 8.3 Run `openspec validate add-agent-control-mod --strict`; verify it passes

## Workflow follow-up

- Archive the change once 1.21.1 NeoForge and Fabric are verified.
- Follow-up changes: `26.1` targets (per D9), server-side control, optional Baritone delegation.

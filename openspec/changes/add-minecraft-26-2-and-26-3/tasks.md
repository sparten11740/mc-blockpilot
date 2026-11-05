# Tasks

## 1. Build: preprocessed sources and the new nodes

- [x] 1.1 Send `src/<version_sources>/` through Stonecutter in `build.fabric`, `build.neoforge` and `build.vanilla`, and `src/vanilla/` in `build.vanilla`, disabling the extra sets' compile and merge tasks (D1); verify `./gradlew build` passes on the five existing nodes with unchanged jar contents, and that "Set active project to 26.1-fabric" and back leaves `src/26.1/` unmodified in Git.
- [x] 1.2 Declare the 26.2 and 26.3 nodes in `settings.gradle.kts` with `versions/<node>/gradle.properties` as pinned in D2; verify `./gradlew projects` lists eleven nodes each new node reaches `compileJava` with dependencies resolved, and "Set active project to 26.2-vanilla" and back leaves `src/26.1/` and `src/vanilla/` unmodified in Git.
- [x] 1.3 Update "To add a target" and the layout notes in `docs/building.md` (`src/26.1/` serves 26.1 and later and goes through Stonecutter; no copy of `src/<version>/` unless a range truly diverges); verify the steps match what 1.2 did.
- [x] 1.4 Write the node's Minecraft version to the jar's manifest and have `BlockpilotAgent` read it instead of its constant (D8); verify the `26.1-vanilla` jar's manifest names 26.1.2 and the agent refuses another version under `java -version` (D10); 2.4 takes both into the script, and 4.4 and 6.4 cover the other jars.

## 2. A test script for every target

- [x] 2.1 Add the input assertions of D10 to `tools/smoke_test.py` (mouse buttons in a world and a screen, camera and cursor, typing and select-all, shift-click, a key the game polls, F8 refused, scroll); verify they pass on a running 26.1 and 1.21.1 client, and that each fails once when what it checks is deliberately broken.
- [x] 2.2 Add the capture and observation assertions of D10 (`pixels` upright, a coloured block by `@<pos>`, an ffmpeg-decoded video frame, `perf` rebuilds and block-entity times and particles, `hud`, graphics options by version), with a missing ffmpeg reported as skipped; verify they pass on a 26.1 and a 1.21.1 client, and each fails once when deliberately broken.
- [x] 2.3 Add `tools/test_targets.py` as in D10: targets from `settings.gradle.kts`, `--targets`, start and wait for the client, smoke test in text and JSON mode, quit, shape comparison per loader, a per-target table, non-zero exit naming target and assertion; verify a passing run exits 0, a planted failure exits non-zero naming both, a client that can't start, one lost during its assertions and one already running are each reported by name with the next target still running, and an interrupt, even twice, leaves no game running.
- [x] 2.4 Add the jar checks and the wrong-version agent check of D10 to `tools/test_targets.py`; verify they pass on the five existing targets and fail on a jar with a wrong name or a missing manifest attribute.
- [x] 2.5 Document running the script in `docs/building.md`, including `--targets`, how long it takes, leaving the game windows alone, and what it can't assert; verify `--list` and `--targets` on a target that builds run as written; 9.1 confirms the rest and the length.
- [x] 2.6 Run `tools/test_targets.py --targets` on the five existing targets on the tree before any port work (`bbf7de1`), and save the per-loader shapes; verify every assertion passes, so failures on the same targets come from the port.

## 3. 26.2: shared code

- [x] 3.1 Add rename rules from 26.2 for the moved screen, overlay, camera, framebuffer and section reads (D3); verify every error of those kinds is gone from `26.2-fabric:compileJava` and the 1.21.1 and 26.1 nodes still compile.
- [x] 3.2 Add the HUD visibility class and move all 15 `options.hideGui` uses to it (D3); verify the nodes that compiled before still do, and the script's `hud` assertions pass on 1.21.1 and 26.1.
- [x] 3.3 Read particles through a `ParticleGroup` accessor on every 26.x version (D3); verify the nodes that compiled before still do, and the script's `perf` assertions pass on 26.1.

## 4. 26.2: rendering and mixins

- [x] 4.1 Port `FrameCapture` and `FrameReadback` to the 26.2 GPU API with inline conditions (D6); verify `26.2-fabric:compileJava` has no errors left.
- [x] 4.2 Retarget `GuiMixin` to `Hud` and `RebuildTaskMixin` to `CompileTask`, and make the `GraphicsPresetMixin` handler static by condition (D4, D5); verify the 26.1 and 26.2 nodes compile.
- [x] 4.3 Move the vanilla HUD hook to `Hud` and the shutdown hook to `exitWorldAndClose` (D4); verify `26.2-vanilla` compiles and its client closes cleanly, putting back an option an agent changed.
- [x] 4.4 Run `tools/test_targets.py --targets` on `26.2-neoforge`, `26.2-fabric` and `26.2-vanilla`, fixing any injection point that stops the game (D9); verify every assertion passes and the shapes match the saved ones of the same loader.

## 5. 26.3: input without GLFW

- [x] 5.1 Replace GLFW constants that have an `InputConstants` equivalent on every version (`PRESS`, `RELEASE`, `KEY_F8`) and rename `Type.KEYSYM` from 26.3 (D7); verify the nodes up to 26.2 compile, and the script's F8 and input assertions pass on 1.21.1 and 26.1.
- [x] 5.2 Add 26.3 conditions to `Injection` for the modifier table and SDL keycodes, and to `Focus` for SDL's window flags (D7); verify `26.3-fabric:compileJava` reports no GLFW errors.
- [x] 5.3 Map mouse button names to the game's numbers by version, keeping `KeyNames.BUTTONS`' order, with `KeyNamesTest` following (D7); verify unit tests and `GrammarConformanceTest` pass on all compiling nodes and the script's mouse assertions pass on 1.21.1 and 26.1.
- [x] 5.4 Update `MouseHandlerMixin` for the five-argument `onMove` with computed relative movement, and `InputConstantsMixin` for `isKeyDown(int)` (D7); verify the 26.1 and 26.2 nodes compile; the mixins apply on 26.3 at 6.4 (D9).

## 6. 26.3: the rest of the port

- [x] 6.1 Add rename rules for the GPU classes' move to `com.mojang.renderpearl.api`, and update `GameRendererMixin` for `renderLevel()` and its target (D5, D6); verify those errors are gone from `26.3-fabric:compileJava`.
- [x] 6.2 Build the server command source with 26.3's `CommandSourceStack` constructor by condition; verify `26.3-fabric:compileJava` has no errors left and the smoke test's `server` steps pass on 26.1.
- [x] 6.3 Note in `docs/commands.md` that key names are the running game's, so 26.3 has no `f25` and adds new ones, and assert in the script that `key press f25` on 26.3 gives `err syntax` with close names; verify the assertion passes once 6.4 runs.
- [x] 6.4 Run `tools/test_targets.py --targets` on `26.3-neoforge`, `26.3-fabric` and `26.3-vanilla`, fixing any injection point that stops the game (D9); verify every assertion passes and the shapes match the saved ones of the same loader.

## 7. Vanilla agent on each version

- [x] 7.1 Cover the 26.2 and 26.3 agents in `docs/connecting.md`; verify the jar names and steps match what the build produces.

## 8. Release and docs

- [x] 8.1 Expect eleven jars in `.github/workflows/release.yml` and add the four new CurseForge rows; verify with `actionlint` or a YAML parse that the workflow is valid and the matrix has eight rows, and that the jar names match the upload globs.
- [x] 8.2 Update `README.md`, `docs/building.md`, `docs/dev-environment.md` and `docs/sable.md` for the new targets; verify `grep -rn '26\.1' README.md docs` shows only what is still true of 26.1 itself.

## 9. Integration checks

- [x] 9.1 Run `./gradlew build` from a clean tree and then `tools/test_targets.py` on all eleven targets; verify exactly eleven mod jars land in `build/libs/`, all unit tests pass on every node, and the script exits 0, then run that script on the tree of 2.6 on the same five targets; verify every assertion passes and the shapes equal this run's.
- [x] 9.2 Confirm the one API jar still serves every target (`extension-api`); verify `RegistryTest` passes on all nodes, and the API jar's classes reference no Minecraft or loader type.
- [x] 9.3 Try `screenshot` and `record` once on the Vulkan backend on 26.3, where the machine supports it (a non-goal, not a gate); report the result with the change.

## 10. Checks only a person can do

- [ ] 10.1 By hand on a 26.2 client: add a 26.2 installation in the Minecraft Launcher, append `-javaagent:<path to the jar>` to its JVM arguments and start it; verify `<game dir>/blockpilot/endpoint.json` appears and `status` answers in a world.
- [ ] 10.2 By hand on a 26.3 client: the same as 10.1 with a 26.3 installation and the 26.3 jar; verify the same, and that `docs/connecting.md` matches the steps.
- [ ] 10.3 By hand on a 26.3 dev client, each during its own agent `wait 400t`: move the real mouse, click left, then right, then middle, scroll, and press W; verify each ends the wait with `err interrupted` naming its input, and left says `left`. Then press the real F8; verify it turns agent control off and on, as on 26.1. Then, on a screen, have an agent run `cursor 40,40`, move the real mouse, and open another screen with a real click; verify `screen` reports the cursor where the real pointer is.
- [ ] 10.4 By hand on a 26.3 dev client, in a world: in a terminal run `sleep 20; <launcher> "wait 400t"`, then in the game move the mouse one way by more than the window's width, stop, and once the wait has begun keep moving briskly that way; verify it ends with `err interrupted input=move`.
- [ ] 10.5 By hand on Windows or Linux with OpenGL 4.4 or newer, on a 26.1 and on a 26.2 or 26.3 client: run `tools/smoke_test.py --game-dir <run dir>` and look at a recorded video's last frames; verify it passes and they are the current ones. This Mac's OpenGL has no `GL_ARB_buffer_storage`, so that path has never run.
- [ ] 10.6 By hand on a 26.3 client with an AZERTY and with a non-Latin keyboard layout: in the chat box have an agent run `type hello`, the select-all chord and `type x`; verify the text is replaced. This Mac's layout wasn't changed to try it.
- [ ] 10.7 By hand on a 26.2 and a 26.3 client: look at a screenshot and a recorded video; verify they look right beyond the orientation and colours the script checks.
- [ ] 10.8 By hand on a 26.3 client with another app in front, never clicking the game: during an agent's `wait 400t`, move the real mouse over its window; verify the wait ends `ok`. Then have the agent run `key escape` and `close`; verify the real pointer stays where it is, then click into the game; verify the mouse turns the camera.
- [ ] 10.9 By hand in the CurseForge app on 26.2 and on 26.3, if it offers them: create a vanilla instance, put the jar in its `mods` folder and add `-javaagent:mods/<jar>` to its Java arguments; verify `endpoint.json` appears and `status` answers in a world, then take the caveat out of `docs/connecting.md` and `README.md`.

## Workflow follow-up

- Archive the change after the project's review requirements are satisfied, and verify `multi-target-build`, `vanilla-agent`, `extension-api` and `video-recording` carry the deltas.
- A release is separate: bump `mod_version`, add the `CHANGELOG.md` section, and watch the CurseForge rows for 26.2 and 26.3 on the first tag.

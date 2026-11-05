# Tasks

## 1. Stack and baseline

- [x] 1.1 Rebase this branch onto `main`, which holds `add-minecraft-26-2-and-26-3`, and verify `git merge-base --is-ancestor` passes and `./gradlew build --continue` is green on all 11 nodes
- [ ] 1.2 Record the D11 baseline for all 11 targets outside the repo (jar class list, manifest, metadata, full `help` output, JSON shapes) and verify each target has all of them

## 2. Rename rules

- [ ] 2.1 Delete the unused `.getNormal()` rule and add `RenameRulesTest` (every rule has a use; no rule's new text in `src/main` or `src/1.21.1`); verify it fails on a deliberately unused rule and passes after removing that rule
- [ ] 2.2 Turn `pass.draw`, `pass.setPipeline`, `pass.bindTexture`, `renderer.countRenderedSections` and `@Inject(method = "destroy",` into conditions; verify all 11 nodes build and the D11 four-target run passes, including `record` and `settle`

## 3. Shared mixins

- [ ] 3.1 Move the 23 byte-identical mixins to `src/main` and add a test that fails on a byte-identical file in two trees; verify the class lists match the baseline, switching the active node to `26.3-fabric` and back leaves `src/` unchanged, and the D11 four-target run passes
- [ ] 3.2 Fold in each of the 7 near-identical mixins that fits D1's two-block limit, and list the ones that stay and why in `design.md`; verify the D11 four-target run passes
- [ ] 3.3 Update the source-tree description in `docs/building.md`, and verify every file and tree it names exists

## 4. Screens adapter

- [ ] 4.1 Add `ClientScreens`, move every screen and overlay use to it and delete its 5 rules; verify no `minecraft.screen`, `setScreen(` or `getOverlay()` remains outside it, and the D11 four-target run passes

## 5. Window system adapter

- [ ] 5.1 Add `WindowSystem` and move focus, the key's other code, button numbers, modifiers and the keyboard key type into it; verify `KeyNamesTest` still passes without the game's libraries, and the input assertions pass on the D11 four targets
- [ ] 5.2 Add the test allowing GLFW and SDL imports only in `WindowSystem` and mixins; verify it fails on a deliberate stray import and passes without it

## 6. GPU adapter

- [ ] 6.1 Add `Gpu` in `src/26.1`, move mapping, the blit, the render target, pixel size and the fence calls into it and delete the rules it replaces; verify the frame, pixel and video assertions pass on `26.1-neoforge`, `26.2-neoforge`, `26.3-fabric` and `26.3-vanilla`

## 7. Loader seam

- [ ] 7.1 Add the render-stage hooks and `cameraRoll` to `ClientPlatform`, and add tests forbidding `game` imports in loader packages and loader conditions outside `platform`; verify both tests fail on a deliberate violation, and `perf` still reports render stages on NeoForge
- [ ] 7.2 Move `minecraftVersion` into `ClientPlatform`, default `isModLoaded`, share category registration, and move `FabricServices` and `NeoForgeServices` out of the entry points; verify all 11 nodes build and the D11 four-target run passes

## 8. Target source of truth

- [x] 8.1 Make `test_targets.py` fail at the start when `settings.gradle.kts`, `versions/` and the `release.yml` rows disagree; verify it fails on a wrapped `target(` line, a missing folder and a missing row, and passes on the real files
- [ ] 8.2 Derive the release job's jar count from `versions/`. In "To add a target" in `docs/building.md`, drop the step that updates it and say that `test_targets.py` fails on a missing `release.yml` row; verify the job's count command prints 11 when run locally, and that the workflow parses as YAML

## 9. Build scripts

- [ ] 9.1 Add `common.gradle.kts` and move what the three scripts share into it; verify every target's class list, manifest and metadata match the baseline, and `publishToMavenLocal` still produces the same coordinates

## 10. Oversized classes

- [ ] 10.1 Split `ActionCommands` per D9 and verify the full `help` output, including command order, matches the baseline, and the D11 four-target run passes
- [ ] 10.2 Move `Perf`'s state and logic to `PerfCapture` without changing its static API or locks; verify `PerfTest` and the `perf` assertions pass on the D11 four targets, and that `Perf` has no static capture state left besides `active` and the capture counter
- [ ] 10.3 Share the `Timed` base class between `PerfCommands` and `RecordCommands`; verify `perf` and `record` with a duration pass, and cancelling either still leaves it running

## 11. Package layering

- [ ] 11.1 Create `game/common`, move the D10 classes (and `Rel` and `Screenshots` to their new homes), and add the test for the dependency order across all source trees; verify it fails on a deliberate back-import and all 11 nodes build
- [ ] 11.2 Move `Json` and `McpTools` to `dev.blockpilot.wire`, add a JDK-only test for it and forbid `protocol` importing `cli`; verify the launcher serves MCP over stdio from a built jar on a bare JVM with no game running

## 12. Integration

- [ ] 12.1 From a clean build, run the full `python3 tools/test_targets.py`, then compare `help` and JSON shapes with the baseline and class lists with the moves in this change; verify exit 0, no skips, and no differences beyond those moves
- [ ] 12.2 By hand, for Jan, on a 1.21.1 and a 26.3 dev client with an agent driving: real keys, clicks and mouse movement take control back, and F8 toggles agent control; verify each by its `err interrupted` reply and the on-screen status line

## Workflow follow-up

- Merge into `main`, then archive this change.
- Pushing stays on hold until Jan lifts it.

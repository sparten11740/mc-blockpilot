# Tasks

## 1. Camera moves in the background (client-observation)

- [x] 1.1 Add a smoke check for the spec's scenarios: a move in the background that answers at once, a server step and a `status` while it runs about halfway, `camera wait` at its end, `camera wait` with nothing moving, and `stop` from a second call freezing it; verify on the 1.21.1 NeoForge dev client that before the change it fails with `err syntax` for `background`
- [x] 1.2 Count client ticks in `CameraControl` and take a move's progress from them (D1), keeping the foreground move's timing; verify the existing camera move checks pass on the 1.21.1 NeoForge dev client
- [x] 1.3 Add `background` to `camera move`, the move under way and its end in `CameraControl` (D2), `stop` and real input ending it through `GameBridge` (D3), and `camera wait` (D4); verify the new check passes on the 1.21.1 NeoForge and 26.3 Fabric dev clients
- [x] 1.4 Regenerate `docs/grammar.ebnf` with `tools/grammar.py` on the 1.21.1 NeoForge dev client and `docs/grammar.xhtml` with `./gradlew grammarDiagrams`; verify the grammar file and conformance tests and the smoke test's grammar check pass
- [x] 1.5 Describe `background` and `camera wait` in the `camera` help, the `camera` row of `docs/commands.md` and the 1.1.0 section of `CHANGELOG.md`; verify `help camera` shows both and says the same as the spec

- [x] 1.6 Sample the corner check every tick and merge a late frame's rows in the constant-speed check and drop a late last row, sample the easing check every tick and skip a late frame's row, accept a background move stopped anywhere short of its end, and stop requiring the block in an away move's reply, as the modified scenarios say; keep the player still on both versions while a camera is away; verify the camera and away checks pass on the 1.21.1 NeoForge and 26.3 Fabric dev clients

## 2. Integration checks

- [ ] 2.1 Run `tools/test_targets.py` on all 11 targets; verify every target passes, including the new check

## Workflow follow-up

- Archive this change.
- Pushing needs Jan's go-ahead.

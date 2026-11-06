# Tasks

## 1. A camera command of its own (client-observation)

- [x] 1.1 Move the `camera` command's declaration, handler and helpers from `ObservationCommands` into a new `CameraCommands`, registered where `camera` was, and `rendered`, which both use, into `CameraControl` (D1), with no change of behaviour; verify the unit tests and the camera checks pass on the 1.21.1 NeoForge dev client and `tools/grammar.py` leaves `docs/grammar.ebnf` unchanged
- [x] 1.2 Take what a timed motion shares out of `CameraMove` into an abstract `CameraMotion`, and have `CameraControl` hold any `CameraMotion` as the move under way (D2); verify the camera checks, including the background ones, pass on the 1.21.1 NeoForge dev client

## 2. Following an entity (client-observation)

- [x] 2.1 Add a smoke check, which needs `entities-by-name`, for the follow scenarios with a tagged armor stand: the framing goes along, over the shoulder (finding out whether a teleport turns an armor stand's body yaw, and using another entity or its yaw if not), `stop` leaves a follow, the entity goes and an absolute offset; verify on the 1.21.1 NeoForge dev client that before the change it fails with `err syntax` for `follow`
- [x] 2.2 Add the offset turned about the vertical by an angle and the smoothed heading as pure functions beside `CameraPath`'s, with unit tests in `CameraPathTest` for a quarter turn each way, a half turn, a zero offset, the short way round past south, and a heading that moves only when the clock does; verify the unit tests pass
- [x] 2.3 Add `camera follow` with `at` and `turning` (D4, D5) and its help; verify the follow check passes on the 1.21.1 NeoForge and 26.3 Fabric dev clients

## 3. Orbiting (client-observation)

- [x] 3.1 Add a smoke check, which needs `entities-by-name`, for the orbit scenarios: a quarter turn, round a moving entity, the centre goes, waiting for one in the background, and an orbit from a camera placed away; verify on the 1.21.1 NeoForge dev client that before the change it fails with `err syntax` for `orbit`
- [x] 3.2 Add `CameraOrbit` (D3) on the turned offset from 2.2, and the `orbit` form with `by`, `background` and `ease=`, letting the `ease=` guard take `orbit`, and its help; verify the orbit check passes on the 1.21.1 NeoForge and 26.3 Fabric dev clients

## 4. Documentation

- [x] 4.1 Regenerate `docs/grammar.ebnf` with `tools/grammar.py` on the 1.21.1 NeoForge dev client and `docs/grammar.xhtml` with `./gradlew grammarDiagrams`, and describe both forms in the `camera` row of `docs/commands.md`, the README's camera row and the 1.1.0 section of `CHANGELOG.md`; verify the grammar checks pass and `help camera` says the same as the spec

## 5. Integration checks

- [ ] 5.1 Run `tools/test_targets.py` on all 11 targets; verify every target passes, including the new checks

## Workflow follow-up

- Archive this change.
- Pushing needs Jan's go-ahead.

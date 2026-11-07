# Tasks

## 1. Field of view and roll (client-observation)

- [x] 1.1 Add a smoke check for the spec's scenarios: a narrow view, a tilted horizon, a zoom along a move, kept through the next placement and out of range; verify on the 1.21.1 NeoForge dev client that before the change it fails with `err syntax` for `fov=`
- [x] 1.2 Add `CameraControl.Lens` to `Placement` (D1) and the glide in `CameraMotion` (D2), with the interpolation, the short way round for roll and the player's start value as pure functions beside `CameraPath`'s and unit tests for them in `CameraPathTest`; verify the unit tests and the existing camera checks pass on the 1.21.1 NeoForge dev client
- [x] 1.3 Apply the field of view through the mixins of both versions at the methods D3 names, and the roll in the placing hook right after it sets the rotation, confirming that NeoForge 1.21.1's camera-angle event runs before that hook and that 26.x builds its view matrix from the rotation, and report the lens's roll in `recordRendered`; verify with a lens set by a temporary line that is not committed that the narrow view and tilted horizon read right on the 1.21.1 NeoForge and 26.3 Fabric dev clients, the roll's sign included
- [x] 1.4 Add the options with their range checks (D4) to the forms that place a camera, and `cam_fov` to `status`; verify the new check passes on the 1.21.1 NeoForge and 26.3 Fabric dev clients
- [x] 1.5 Describe both options in the `camera` help, the `camera` row of `docs/commands.md`, the README and the 1.1.0 section of `CHANGELOG.md`, and `cam_fov` among `status`'s fields; verify `help camera` says the same as the spec and the JSON shapes check passes

## 2. Integration checks

- [x] 2.1 Run `tools/test_targets.py` on all 11 targets; verify every target passes, including the new check

## Workflow follow-up

- Archive this change.
- Pushing needs Jan's go-ahead.

# Tasks

## 1. Spike (video-recording)

- [ ] 1.1 Drive the client's clock by a fixed step per frame while the integrated server is frozen and stepped as the clock crosses each tick, on 1.21.1 and 26.x (D1); check ticks, partial ticks, camera moves, interpolation and input; write the findings into the design; if the clock can't be driven safely, delete the change rather than archive a spec without code

## 2. Exact takes (video-recording)

- [ ] 2.1 Add a smoke check for the spec's scenarios: smooth under load (with the load command as it is then named), sound refused, and a failed take leaving the server's tick state, the failure caused by pointing the config's `ffmpeg` at a program that exits at once; cover agent control off and the game closing by having every ending call the one restore routine, tested on its own; confirm `tick query`'s `frozen` field name when writing the check; verify it fails with `err syntax` for `exact` before the change
- [ ] 2.2 Add the clock step and the timer mixins (D2), the server stepping and its restore (D3), and `exact` on `record start`, `script` and `<duration>`; verify the check passes on the 1.21.1 NeoForge and 26.3 Fabric dev clients
- [ ] 2.3 Describe `exact` in the help, `docs/recording.md` and the 1.1.0 section of `CHANGELOG.md`; regenerate the grammar files; verify the grammar checks pass

## 3. Integration checks

- [ ] 3.1 Run `tools/test_targets.py` on all 11 targets; verify every target passes, including the new check

## Workflow follow-up

- Archive this change.
- Pushing needs Jan's go-ahead.

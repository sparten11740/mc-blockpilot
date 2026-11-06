# Tasks

## 1. Slow motion by the tick rate (video-recording)

- [ ] 1.1 Add a smoke check for the spec's scenarios: the game time over a 2 s take at `tick rate 5`, the wall time of a 20-tick camera move then, and the time of a mark after `wait 20t`; verify it passes on the 1.21.1 NeoForge and 26.3 Fabric dev clients (it documents and pins existing behaviour, so there is no failing-first run)
- [ ] 1.2 Add the slow-motion recipe to `docs/recording.md` and a line to the 1.1.0 section of `CHANGELOG.md`; verify they say the same as the spec

## 2. Integration checks

- [ ] 2.1 Run `tools/test_targets.py` on all 11 targets; verify every target passes, including the new check

## Workflow follow-up

- Archive this change.
- Pushing needs Jan's go-ahead.

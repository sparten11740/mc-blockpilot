# Tasks

## 1. Surroundings of an away camera (game-perception)

- [x] 1.1 Add a smoke check for the spec's scenarios with an armor stand by a camera placed away; verify on the 1.21.1 NeoForge dev client that before the change `look` doesn't list it and `find armor_stand 8` answers `ok none`
- [x] 1.2 Add `AwayCamera.clientPresence()` (D1) and centre `look` and `find` on it (D2), and have `NamedEntities` use it in place of its own; verify the new check and the `@<name>` check pass on the 1.21.1 NeoForge and 26.3 Fabric dev clients
- [x] 1.3 Say in the help of `look` and `find`, `docs/commands.md` and the 1.1.0 section of `CHANGELOG.md` that they search around a camera placed away; verify `help look` and `help find` say the same as the spec

## 2. Integration checks

- [ ] 2.1 Run `tools/test_targets.py` on all 11 targets; verify every target passes, including the new check

## Workflow follow-up

- Archive this change.
- Pushing needs Jan's go-ahead.

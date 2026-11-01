# Tasks

## 1. Key chords (player-control)

- [x] 1.1 Add `KeyNames.chord` (D1) with `KeyNamesTest` cases for a single key, a chord and an empty part, and make `key` press, hold and release chords (D2, D3), with the `key` help and the README input row; verify the tests pass, and on the 1.21.1 dev client that `key f3+b` shows hitboxes and a second `key f3+b` hides them with the debug screen closed, that after `key down left_shift+w` `status` shows the player sneaking and moving, and after `key up left_shift+w` standing still and not sneaking, and that `key f3+` answers `err syntax`

## 2. Integration checks

- [ ] 2.1 Run `./gradlew build` for all four targets and the smoke test against the 1.21.1 dev client; verify all pass

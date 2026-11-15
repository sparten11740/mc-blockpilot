# Tasks

## 1. Shot list (client-observation)

- [x] 1.1 Add `CommandRegistry.parse`, the `play` form and `CameraTimeline` (D1, D2), the name lookup (D3) and the endings (D4); verify on the 1.21.1 NeoForge client that a five-shot play answers at once, the camera follows the shots while the script goes on, `camera wait` answers at the end, a `camera at` ends it, and bad shots and a shot that can't start fail as specified
- [x] 1.2 Add a smoke check of a cut, a hold and a move, `camera wait` and a refused shot

## 2. Docs and checks

- [x] 2.1 Describe it in the help, docs/commands.md, docs/recording.md and the CHANGELOG under 1.4.0; rewrite docs/grammar.ebnf from the client with Sable and redraw the diagrams
- [x] 2.2 Compile all targets, run the unit tests and the test script on 1.21.1 NeoForge, 1.20.1 Forge and 26.3 Fabric; verify all pass

## Workflow follow-up

- Archive this change.
- Pushing needs the maintainer's go-ahead.

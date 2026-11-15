# Tasks

## 1. Slot type and grammar (extension-api, command-language)

- [x] 1.1 Add `ArgType.SUB_LEVEL` and `Lexicon.isSubLevel`, the overlap with `ID`, the `sub-level` productions in the core grammar and its delta, and make the API 2.2.0 (D1); verify the lexicon, slot type and grammar tests pass

## 2. Camera (sable-sublevels)

- [x] 2.1 Resolve `sub:<ref>` through the Sable layer, add the origin point (D2) and the world-axis follow (D3), take sub-levels in `follow`, `orbit` and `look`, and `around` in `move` (D4); verify on the 1.21.1 NeoForge client with Sable 2.0.6, on a falling ship, that `follow` keeps its offset, `orbit` its distance, `look` its aim, and `move … around` ends at its offset, and that an unknown ship and `turning` fail
- [x] 2.2 Add a smoke check that a `sub:` target parses as subject, centre, look and frame and answers `no_sublevel` or `unsupported`

## 3. Docs and checks

- [x] 3.1 Describe it in the help, docs/commands.md, docs/sable.md, docs/extending.md and the CHANGELOG under 1.4.0; rewrite docs/grammar.ebnf from the client with Sable and redraw the diagrams
- [x] 3.2 Compile all targets, run the unit tests, and run the test script on 1.21.1 NeoForge, 1.20.1 Forge and 26.3 Fabric without Sable; verify all pass

## Workflow follow-up

- Archive this change.
- Pushing needs the maintainer's go-ahead.

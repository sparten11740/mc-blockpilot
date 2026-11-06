# Tasks

## 1. Entities by name (command-language)

- [x] 1.1 Add `Lexicon.isEntityName` and let `ArgType.ENTITY` match `e<id>` or `@<name>` (D1, D3), keeping `EntityRef` an id and making `Args.entity` refuse an unresolved name with a clear `IllegalStateException`; change the grammar's `entity` production in `Grammar.CORE`; add lexicon, args, parser and grammar tests for `@pip`, `@a.b-c_d`, and the refusals `@p`, `@1x`, `@`, `@e[type=cow]`; verify the api coverage rule and `theCoreGrammarIsTheSpecs` pass
- [x] 1.2 Add `GameBridge.resolveEntity` and the protocol helper that resolves `@<name>` arguments before a handler runs (D1), used for scripts' calls and queries and for `wait` and `sample`; add engine tests with names in the fake game for a query, an action, a server-thread query, a repeated entity slot, a text argument left as written, `wait … until` that waits for a name to appear, `sample` and an unknown name; verify the protocol tests pass
- [x] 1.3 Look names up in the game (D2): on the server thread by tag and custom name, on the client by custom name and through the integrated server by tag, nearest where the server takes the player to be (`AwayCamera.centre()` or the player on the client, `AwayCamera.presence` on the server); add a smoke check summoning two cows tagged `star`, one far off, and a named cow, running `entity @star`, `nbt @star` on the server thread, `camera at ~0,2,-6 look @star`, `face @daisy`, `entity @star` and `nbt @star` again with a camera placed away beside the far star, and `entity @nobody`; verify it fails before and passes on the 1.21.1 NeoForge and 26.3 Fabric dev clients
- [x] 1.4 Say in `help`, the MCP `run` tool's description, the help of the commands that take entities through their slot description, `docs/command-language.md`, `docs/extending.md`, the README and the 1.1.0 section of `CHANGELOG.md` that entities are `e<id>` or `@<name>`; regenerate `docs/grammar.ebnf` and `docs/grammar.xhtml`; verify the grammar checks pass

## 2. Integration checks

- [ ] 2.1 Run `tools/test_targets.py` on all 11 targets; verify every target passes, including the new check

## Workflow follow-up

- Archive this change.
- Pushing needs Jan's go-ahead.

# Tasks

## 1. Endings (extension-api)

- [x] 1.1 Add `CommandContext.atEnd` with its refusing default, and endings in `ScriptJob` after the last statement and after a failure (D1); add engine tests for an ending after the last step and after a failing step, its event in the reply, the order of two endings, the 60 s limit and a built-in self-timed ending past it, and an api test for the default refusing; verify the protocol tests and the api coverage rule pass
- [x] 1.2 Run endings after a cancel too, with the engine moving a cancelled job that still has endings to `free` (D2); add engine tests for `stop` and a closed session with an ending, and for a script queued behind it running meanwhile; verify the protocol tests pass

## 2. A recording that ends with its script (video-recording)

- [x] 2.1 Add a smoke check for the spec's scenarios: filmed to the end, a failing step, stopped from another call and stopped by the script with a later recording left running; verify on the 1.21.1 NeoForge dev client that before the change it fails with `err syntax` for `script`
- [x] 2.2 Add the `script` form and its ending to `record` (D3) and its help; verify the new check passes on the 1.21.1 NeoForge and 26.3 Fabric dev clients
- [x] 2.3 Regenerate `docs/grammar.ebnf` with `tools/grammar.py` and `docs/grammar.xhtml` with `./gradlew grammarDiagrams`, and describe `record script` in `docs/recording.md` and `atEnd` in `docs/extending.md` and the 1.1.0 section of `CHANGELOG.md`; verify the grammar checks pass and `help record` says the same as the spec

## 3. Integration checks

- [x] 3.1 Run `tools/test_targets.py` on all 11 targets; verify every target passes, including the new check

## Workflow follow-up

- Archive this change.
- Pushing needs Jan's go-ahead.

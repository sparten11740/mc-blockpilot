# Tasks

## 1. Playing a sound on the client (client-observation)

- [ ] 1.1 Add smoke steps for the spec's scenarios: a bell at a placed camera (`camera at ~40,2,0; sound minecraft:block.bell.use at ~40,0,3; sounds playing match=block.bell.use; camera reset`), an interface click (`sounds` lists `ui`), a muted category with `option soundCategory_block 0` reset afterwards, an unknown id, `pitch=3`, and `sounds match=minecraft:block.bell.uze` naming `minecraft:block.bell.use`; verify on the 1.21.1 NeoForge dev client that before the change each `sound` step fails with `err syntax` for the unknown command, and note the `sounds match=` answer
- [ ] 1.2 Move the known-id check of `sounds match=` into one helper (D2); verify on the 1.21.1 NeoForge dev client that the `sounds match=minecraft:block.bell.uze` step answers word for word as before
- [ ] 1.3 Add `sound <id> [at <pos>] [volume=] [pitch=] [source=]` playing a `SimpleSoundInstance` through `SoundManager.play` as an action (D1, D3), answering with id, position or `ui`, volume, pitch, category, tick and `muted` (D5), and failing with `err syntax` for an unknown id, a volume below 0 or a pitch outside 0.5 to 2 (D4); verify the new smoke steps pass on the 1.21.1 NeoForge and 26.3 Fabric dev clients, and by ear in `record 3s` from `camera at ~40,2,0` that the bell at `~40,0,3` sounds close
- [ ] 1.4 Regenerate `docs/grammar.ebnf` with `tools/grammar.py` on the 1.21.1 NeoForge dev client with Sable and `docs/grammar.xhtml` with `./gradlew grammarDiagrams`; verify the `sound` production is in both, the smoke test's grammar check passes and `theDiagramsShowExactlyTheProductions` passes
- [ ] 1.5 Describe `sound` in its help, name it in the help of `sounds` and the other way round, and add a `sound` row beside `sounds` in `docs/commands.md`; verify `help sound` on the dev client shows it and says the same as the spec

## 2. Integration checks

- [ ] 2.1 Run `tools/test_targets.py` on all 11 targets from a clean build; verify every target passes, including the new steps

## Workflow follow-up

- Merge into `main`, then archive this change.
- Pushing needs Jan's go-ahead.

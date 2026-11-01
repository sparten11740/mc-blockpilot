# Tasks

## 1. Block state filters (command-language, game-perception, sable-sublevels)

- [x] 1.1 Add the `block-state` token (D2): its productions in this change's command-language delta (a MODIFIED Grammar) and in `Grammar.CORE`, `Lexicon.isBlockState`, `ArgType.BLOCK_STATE`, and `SpecGrammarTest` reading an active change's grammar as `GrammarTest` does (D8); verify with lexicon and spec-grammar tests that `oak_log[axis=y]`, `#logs[axis=y]`, `create:*[a=b]` and `minecraft:oak_log[axis=y,waterlogged=false]` are block states and that `oak_log[]`, `oak_log[axis]`, `oak_log[axis=y` and `oak_log[Axis=y]` are not
- [x] 1.2 Add states to `BlockMatcher` (D3): a pure `BlockToken` parser with unit tests (pairs in order, a state named twice refused), the checks against a block id's state definition, and per-state matching for tags and patterns
- [x] 1.3 Take block states in `find`, `mine` by block, `sable:find` and `sable:tp`, with the help and the README perception, interact and Sable rows; verify on the 1.21.1 dev client that `find oak_log[axis=x] 16` lists only the lying logs, `find #logs[axis=y] 16` only the standing ones, `find stone[axis=x]` and `find oak_log[axis=up]` fail with `err syntax` naming what stone and the axis have, `mine oak_log[axis=x] x2` mines two lying logs, and `sable:find` on a test sub-level finds a slab by `type=top`
- [x] 1.4 Raise `find`'s and `sable:find`'s limit to 1000 (D4), with the help and the README; verify that `find` with 150 matching blocks and limit 1000 lists all 150 without `count`

## 2. Place and blocked-view errors (player-control)

- [x] 2.1 Make `place` aim at the face of the support's shape (D5) through a pure `ShapeBounds` with a regression unit test (a carpet's top face, a fence post's side, a full block's face, an empty shape falling back to the cell face), a smoke-test step placing cobblestone on a carpet, the check that the crosshair hits the support on the side facing the target, and a smoke-test step for a side out of sight; verify on the 1.21.1 dev client that `place cobblestone ~2,1,0` on a carpet at `~2,0,0` failed with "cannot see the face" before the change and places it after, that placing against a fence post from the side facing the player works, and that a side out of sight fails without placing anything (before, the block went beside the face the crosshair hit)
- [x] 2.2 Name what the crosshair hits in `mine`'s "is behind another block" and `place`'s "cannot see the face" errors (D6), with a smoke-test step for `mine` behind stone; verify on the 1.21.1 dev client that `mine ~3,0,0` with stone at `~2,0,0` fails naming stone at `~2,0,0`

## 3. Help and README (D7)

- [x] 3.1 Say in the `sneak`, `sprint`, `key` and `mouse` help and in the README movement and input rows that held input lasts until it is released, `stop`, a failed step of its script, or its connection (MCP session) closing
- [x] 3.2 Give `\;` with an NBT int-array example in `help server` and the README server row

## 4. Integration checks

- [x] 4.1 Run `./gradlew build` for all four targets and the smoke test against the 1.21.1 dev client, in text and JSON mode; verify all pass, including the new steps

## 5. Review follow-ups

- [x] 5.1 Correct the held-input wording in the help and README: a failing step not marked ? of any script with an action on the same connection releases everything the connection holds
- [ ] 5.2 Make `mine` aim at the centre of the block's shape, as `use` does (D9), with a smoke-test regression step mining a carpet by position; verify on the 1.21.1 dev client that the step failed before the change (`err out_of_reach ~0,0,2 is behind grass_block ~0,-1,3`) and passes after, also for a rail
- [ ] 5.3 Add smoke-test steps for block states: `find oak_log[axis=x] 4 1` listing a lying log beside a nearer standing one, and the `err syntax` replies for `oak_log[axis=up]` and `stone[axis=x]`
- [x] 5.4 Make `SpecGrammarTest` read `A - B` across the line-terminator characters a token may hold, and add them to its random alphabet
- [x] 5.5 Add generated block-state tokens, valid and corrupted, to `SpecGrammarTest`'s random inputs
- [ ] 5.6 Check `place`'s whole error in the face-out-of-sight smoke step, and clear a misplaced block in its cleanup
- [x] 5.7 Cache `BlockMatcher`'s answer per block state (D3)
- [x] 5.8 Choose a shape's bounds, or the whole cell for an empty shape, in `ShapeBounds` with a unit test (D5)
- [x] 5.9 Tidy: one state charset in the lexicon, no comment repeating `BLOCK_STATE`'s description, `place`'s face suffix gated on the hit type, no local shadowing `Dig`'s `what`, `Shapes.faceToward` package-private, one find limit constant, `BlockStates` renamed `BlockToken`
- [ ] 5.10 Run `./gradlew build` for all four targets and the smoke test against the 1.21.1 dev client, in text and JSON mode; verify all pass, including the new steps

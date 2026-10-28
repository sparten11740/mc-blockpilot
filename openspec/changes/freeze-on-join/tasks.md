# Tasks

## 1. Frozen join and saved time (tick-control, game-session)

- [x] 1.1 Add the server-started hook on both loaders and `join world tick=frozen <folder>`, freezing the started server when its folder is the pending one, marking the freeze as Blockpilot's and clearing the pending freeze when the join answers, fails or is cancelled, with `join server tick=frozen` failing with `err syntax` before leaving the current game (D1, D2), with the join help and the README's session row; verify on the dev client that `join world tick=frozen <folder>; tick query; tick query` reports `frozen=true` and the same tick twice, that `tick step 1` advances it by one, that `leave` then shows the world unfrozen on a plain `join world`, and that `join server tick=frozen 0` answers `err syntax` and stays in the world
- [ ] 1.2 Record the game time in the server-stopping hook and answer `saved_at=t<tick>` from `leave` and `quit` after a singleplayer world (D3), with their help fields and the README's session row; verify on the dev client that `leave` answers `saved_at`, that a following `join world tick=frozen` of the same folder reports that tick in `tick query`, that the value equals the Time in the save's level.dat, and that `quit` answers `pid` and `saved_at`
- [ ] 1.3 Check Sable after a frozen join and document it in the README's With Sable section; verify on the dev client, with a raft moving when the world is left, that after `join world tick=frozen` `sable:info <raft> side=server` reads the raft at once and stays put over a few ticks, that `sable:list` lacks it, and that after `tick step 1` `sable:list` shows it; the README says what the check showed

## 2. Integration checks

- [ ] 2.1 Run `./gradlew build` for all four targets and the smoke test in both modes against the dev client; verify all pass

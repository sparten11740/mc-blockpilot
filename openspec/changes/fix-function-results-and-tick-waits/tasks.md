# Tasks

## 1. A command without a result (server-bridge)

- [x] 1.1 Add a smoke check that writes `datapacks/smoke/` into the test world (a `pack.mcmeta` with only `"pack_format": 48`, `data/smoke/function/place.mcfunction` setting stone at a cleared position, `seven.mcfunction` holding `return 7`), runs `server reload`, then `server function smoke:place; block <that position> side=server`, `server function smoke:seven` and `?server function smoke:missing`, and clears the block; verify on the 1.21.1 NeoForge dev client that before the change the first answers `err command_failed Running function smoke:place` and the other two answer as the spec says
- [ ] 1.2 Make `server` judge a command whose callback never ran by whether a red failure message reached the source (D1), answering `ok success=true` without `result` when none did; verify the new check passes on the 1.21.1 NeoForge and 26.3 Fabric dev clients, and that `server setblock 0 -60 0 not_a_block` still answers `err command_failed Unknown block type 'minecraft:not_a_block'` with the pointer only as a row
- [ ] 1.3 Say in `server`'s help and the `server` row of `docs/commands.md` that a command without a result, such as a function without `return`, answers `ok success=true` without `result` unless it sent a failure; verify `help server` on the dev client shows it and says the same as the spec

## 2. Tick waits (player-control, command-language)

- [ ] 2.1 Make `WaitTicks` answer on the Nth tick after the one it started in (D2), and update `EngineTest.waitForTicks` (not done after 3 ticks, `ok` on the 4th) and `aSampleRunsBesideAWait` (eleven rows); verify both fail before the change and the protocol tests pass after it
- [ ] 2.2 Add a smoke step `sample status every 1t x1`, forty `wait 1t`, `sample status every 1t x1` that checks the two rows' client ticks `c<n>` are 40 apart; verify on the 1.21.1 NeoForge dev client that it fails before the change (0 apart) and passes after
- [ ] 2.3 Say in `wait`'s help and the `wait` row of `docs/commands.md` that the step after `wait <N>t` runs N ticks after the one before; verify `help wait` on the dev client shows it

## 3. Integration checks

- [ ] 3.1 Run `tools/test_targets.py` on all 11 targets from a clean build; verify every target passes, including the new check and step

## Workflow follow-up

- Merge into `main`, then archive this change.
- Add both fixes to the next release's section of `CHANGELOG.md`, with the note that a script runs a tick longer per tick wait.
- Pushing needs Jan's go-ahead.

# Tasks

## 1. A camera away from the player (client-observation)

- [x] 1.1 Add a smoke check that, in the test world, runs `camera at 1000,-58,0 look 1000,-60,5 away; settle; block 1000,-61,0`, then `server playsound minecraft:block.bell.use master @a 1000 -60 5`, `server summon minecraft:lightning_bolt 1000 -60 5` and `server particle minecraft:flame 1000 -59 5 0.2 0.2 0.2 0 50`, reads `sounds` and `particles` since its start, checks `status; wait 1s; status` give one position, then `camera reset; settle; block ~0,-1,0`; verify on the 1.21.1 NeoForge dev client that before the change it fails (`err syntax` for `away`)
- [x] 1.2 Add `away` to `camera at` with the gate of `server` checked before anything changes (D5), and a server-side record of each player's away camera that the client fills each tick with the camera's absolute position and dimension; verify on a world with commands disabled that `camera at ~0,10,0 away` answers `err no_cheats` and places no camera
- [x] 1.3 Measure chunk tracking, the player's loading and ticking section, and the chunk ticking range from `presence(player)` in the 1.21.1 and 26.1 trees (D1, D2), re-centring once a server tick while away and at once on placing and resetting (D3); verify on the 1.21.1 NeoForge and 26.3 Fabric dev clients that the camera's reply names `air`, `settle` answers `settled=` and `block 1000,-61,0` reads `grass_block`, and that after `camera reset; settle` the block under the player reads again
- [x] 1.4 Measure entity tracking, `PlayerList.broadcast`, `ServerLevel.sendParticles`, `ServerLevel.explode` and `PlaySoundCommand` from `presence(player)` in both trees (D2); verify on the same two clients that `sounds` lists `block.bell.use` at `1000,-60,5` and `entity.lightning_bolt.thunder`, and `particles` counts `flame`, and that without `away` none of the three arrive
- [x] 1.5 Keep the player still while away (D4), and on 1.21.1 its chunk entity-ticking with a ticket of its own (D6); verify on the 1.21.1 NeoForge, 26.2 NeoForge and 26.3 Fabric dev clients that `status; wait 1s; status` give one position while the camera is 1000 blocks away, that `server give @p minecraft:stone; inv` then lists the stone, and that the player walks again after `camera reset; settle`
- [x] 1.6 End the away camera on `camera reset`, `camera off`, a new `camera at` without `away`, leaving the world, a dimension change and agent control off (D5); verify each on the 1.21.1 NeoForge dev client by `block ~0,-1,0` reading the block under the player after `settle`, the dimension change through `server execute in minecraft:the_nether run tp @p 0 70 0`
- [x] 1.7 Centre `settle` on an away camera's chunk (D7); verify on the 1.21.1 NeoForge dev client that `camera at 1000,-58,0 away; settle` answers `settled=` with no `missing=`, and that `settle` without a camera still counts the chunks around the player
- [x] 1.8 With Sable on the 1.21.1 NeoForge dev client, place a test sub-level 1000 blocks from the player and film it with `away`; verify whether `screenshot` shows it, and if it doesn't, say so in the help and the docs as a limit
- [x] 1.9 Regenerate `docs/grammar.ebnf` with `tools/grammar.py` on the 1.21.1 NeoForge dev client with Sable and `docs/grammar.xhtml` with `./gradlew grammarDiagrams`; verify the `camera` production shows `away`, the smoke test's grammar check passes and `theDiagramsShowExactlyTheProductions` passes
- [x] 1.10 Say in `camera`'s help and the `camera` and `settle` rows of `docs/commands.md` what `away` moves to the camera, that the player stands still meanwhile, that how near a player mobs spawn and despawn still goes by the player (summon actors with `{PersistenceRequired:1b}`), and that it needs the integrated server; verify `help camera` on the dev client shows it and says the same as the spec

## 2. Integration checks

- [ ] 2.1 Run `tools/test_targets.py` on all 11 targets from a clean build; verify every target passes, including the new check

## Workflow follow-up

- Merge into `main`, then archive this change.
- Pushing needs Jan's go-ahead.

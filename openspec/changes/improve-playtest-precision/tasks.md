# Tasks

## 1. Server commands from the player (server-bridge)

- [x] 1.1 Build `server`'s default source at the player (D1), say where it runs in `help server` and the README, and add a smoke step that moves the player 40 blocks from the spawn, runs `server setblock ~ ~-1 ~` without `as=player` and reads that block back; verify on the 1.21.1 NeoForge dev client, away from the spawn, that the spec's scenarios hold, the Nether one included

## 2. Use on its target (player-control)

- [x] 2.1 Let `AimedClick` test any hit and arm it for world blocks with a shape and for entities (D2), with `help use` and the README; verify on the dev client that `use` at a lever behind glass fails `obstructed` naming the glass, that a clear lever and a villager are used, that `use` at air still clicks what is aimed at, and that a lever aboard a moving sub-level still works
- [x] 2.2 Add `off_target` to `use … any` (D3) and `sneak_item` to `use … sneak` (D4), with help and README; verify on the dev client that `any` at a lever behind glass reports `off_target=<lever>`, an on-target `any` doesn't, and a sneaking `use` on a chest with cobblestone in hand reports `sneak_item=cobblestone` and opens no screen

## 3. Boarding (sable-sublevels)

- [x] 3.1 Move `sable:tp` to the client thread with its server part in one submitted task (D6), prefer spots in sight of the block and report `dist` and `hidden` (D5), with help and the README's Sable section; verify on the dev client with Sable that a lever inside a cabin next to its window boards the player inside without `hidden`, that a walled-in block reports `hidden`, and that the existing scenario still holds
- [x] 3.2 Add `sable:tp <sub-level> <block|#tag|*pattern>` (D6) with help and README; verify on the dev client that it boards next to the nearest match by UUID, name and `here`, answering with its `block` and `target`, also once the client has dropped the sub-level, and fails `no_block` without moving the player when nothing matches

## 4. Perception and observation replies (game-perception, client-observation)

- [x] 4.1 Report a cut `find` radius as `radius=128`, and say in `help find` and the README that it searches a cube and that `sable:find` finds blocks aboard (D7); verify on the dev client that `find grass_block 400` carries `radius=128` in text and JSON and `find grass_block 64` doesn't
- [x] 4.2 Let `since=` on `sounds`, `messages` and `particles` take a duration (D7), with a unit test for turning a duration into a tick, help and README; verify on the dev client that `sounds since=3s` lists a sound from 40 ticks ago and not one from 100 ticks ago
- [ ] 4.3 Add `t<tick>` to a single `screenshot`'s reply (D7) and to the smoke test's check of it; verify the reply in text and JSON on the dev client
- [ ] 4.4 Write `blocks … grid` as the palette line and one symbol per cell (D7), with help and README; verify on the dev client that a region of modded and vanilla blocks prints each block state once, in the palette, and rows of single symbols

## 5. Integration checks

- [ ] 5.1 Build the four jars and run the smoke test on the 1.21.1 NeoForge dev client; verify all node tests pass and the smoke test passes in text and JSON mode

## Workflow follow-up

- Tell the play-testing session that a new 1.21.1 NeoForge jar is ready, and
  what changed.
- Archive after `improve-playtest-diagnostics`, whose README edits this
  change's build on.

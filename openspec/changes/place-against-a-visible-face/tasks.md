# Tasks

## 1. Place picks a neighbour in sight (player-control)

- [ ] 1.1 Add smoke-test steps for the spec's scenarios: filling a hole two blocks ahead (`block ~0,-1,2 side=server` reads cobblestone), above the eyes beside a block (cobblestone at `~2,2,0` with stone at `~2,0,0`, `~2,1,0` and `~2,2,-1`), the ground before a wall (`block ~2,0,0` reads `torch`, not `wall_torch`), and no neighbour in sight (`err out_of_reach` naming `stone ~2,2,0` on its west face, nothing placed), each clearing what it set; verify on the 26.3 Fabric dev client that the first two fail before the change with the errors seen while proposing (`grass_block ~0,-1,1 on its up face`, `stone ~2,1,0 on its west face`) and the other two pass, as does the existing step for a side out of sight
- [ ] 1.2 Make `place` take the first neighbour in sight (D1 to D4): a sight test beside `seen` in `PlayerActions` that casts the pick's ray from the eyes through the aim point out to `blockInteractionRange()` and checks the block and face it hits, with today's first neighbour when none is in sight or the cell is aboard a sub-level; verify on the 26.3 Fabric and 1.21.1 NeoForge dev clients that the four new smoke steps and the existing place steps pass, and on the 1.21.1 NeoForge client with Sable that `place` for a free cell on a test sub-level's deck still places aboard
- [ ] 1.3 Say in `place`'s help and the `place` row of `docs/commands.md` that it places against the first neighbour, below, above, north, south, west, east, whose face is in sight within reach, else fails at the first; verify `help place` on the dev client shows it and says the same as the spec

## 2. Integration checks

- [ ] 2.1 Run `tools/test_targets.py` on all 11 targets from a clean build; verify every target passes, including the new place steps

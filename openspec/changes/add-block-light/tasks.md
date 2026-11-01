# Tasks

## 1. Light in block (game-perception, sable-sublevels)

- [x] 1.1 Add the `light` keyword to `block` with `sky=` and `block_light=` (D1, D2), the help and the README perception row; verify on the 1.21.1 dev client that `block ~0,0,0 light` in the open by day answers `sky=15 block_light=0`, that the cell beside a torch under a roof answers `block_light=13`, that `side=server` gives the same values, and that `block ~0,-1,0` carries neither field
- [x] 1.2 Add `world_sky=` and `world_block_light=` for a plot position (D3), with the help and the README Sable section; verify on the 1.21.1 dev client with a test sub-level holding a slab that `block <its plot position> light` carries all four fields, also with `side=server`, and that a world position carries no `world_` fields

## 2. Integration checks

- [ ] 2.1 Run `./gradlew build` for all four targets and the smoke test against the 1.21.1 dev client; verify all pass

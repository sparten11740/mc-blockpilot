# Tasks

## 1. Plot positions in camera and face (sable-sublevels)

- [ ] 1.1 Put `camera at … clear`'s look point into the camera point's frame before stepping (D1), and say in the `camera` help that with Sable a plot position follows its sub-level each frame; verify on the 1.21.1 dev client in a world with a sub-level that `camera at <plot position inside one of its blocks> clear` without `look` answers `cleared=` of a few blocks at most with the camera in air, that the same with `look <plot position aboard>` still clears, and that `help camera` names plot positions
- [ ] 1.2 Say in the `face` and `use` help that with Sable a plot position follows its sub-level each tick, and point to it in the README's Sable section; verify `help face` and `help use` show it

## 2. Face reach and the sneak tap (player-control)

- [ ] 2.1 Add `dist=` and `reach=` to `face`'s reply when the crosshair hits nothing (D2), with the help fields and the README movement row; verify on the 1.21.1 dev client in survival that `face` at a block 8 blocks away answers `aim:nothing` with `dist=` above 7 and `reach=4.5`, that `face` at air within reach gives `dist=` below `reach=`, and that `face` at a block in reach carries neither
- [ ] 2.2 Make a bare `sneak` a four-tick tap (D3), with the help and README movement row; verify on the 1.21.1 dev client that `sneak` while riding a minecart leaves it, that `status` afterwards shows no `sneaking`, and that `sneak on` and `sneak off` still hold and release

## 3. Integration checks

- [ ] 3.1 Run `./gradlew build` for all four targets and the smoke test against the 1.21.1 dev client; verify all pass

# Tasks

## 1. Height above ground (sable-sublevels)

- [x] 1.1 Find a sub-level's lowest point by the block with the least `up · pos`, searching sections in order of how low they could reach (D1, D2), measure `agl` from it to the ground straight under it (D3), with `help sable:info` and its `agl` field, the README's With Sable section, and a unit test of the lowest corner and the section bound; verify on the dev client with a sub-level paused by `sable paused true` that level on the ground it reads `agl=0`, lifted level it reads the gap below its lowest block, and rolled 30° and 150° about its long axis it reads the gap below its lowest corner (checked against `sable:world` of that block's corner), while at 150°, with its sparse top layer down, `box=` reaches lower

## 2. Flight readout (sable-sublevels)

- [x] 2.1 Add `y=`, the world y of the sub-level's position, to `sable:info <sub> brief` before `agl` (D4), with `help sable:info`, the `y` field, the README's sable row and With Sable section; verify on the dev client that `sable:info <sub> brief` gives yaw, pitch, roll, speed, climb, y and agl, and that `y` matches the y of `at=` in the full reply

## 3. Integration checks

- [ ] 3.1 Run `./gradlew build` for all four targets and the smoke test in both modes against the dev client; verify all pass

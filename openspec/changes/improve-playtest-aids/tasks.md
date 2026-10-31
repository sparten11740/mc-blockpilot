# Tasks

## 1. Block properties and find positions (game-perception)

- [x] 1.1 Make `block` report every state property, defaults included, through a new helper, keeping `blocks`' palette on non-default ones, with the help field and README perception row (D1); verify on the 1.21.1 dev client that `block` on a placed unlit redstone lamp answers `redstone_lamp lit=false`, that `assert block <pos> props.lit==false` answers `ok props.lit=false`, and that a single chest answers with `facing`, `type` and `waterlogged`
- [x] 1.2 Say in the `find` help and the README's perception row that `~` positions are relative to the player's block as the find runs and that JSON's `at_abs` gives them absolute; verify `help find` shows it and `--json find stone 8 1` carries `at` and `at_abs`

## 2. Screen, entity, camera and place (game-perception, client-observation, player-control)

- [ ] 2.1 List the creative inventory's current-page tabs in `screen` as `t<i>:tab "<name>" @x,y,w,h` with `selected`, through a `ClientPlatform.Services` method for both loaders and a `CreativeModeInventoryScreenAccessor` in both versions (D2), with the `screen` help and README gui row; verify on the 1.21.1 dev client in creative that `key press e; screen` lists the tabs with one `selected`, that clicking the centre of another tab's bounds selects it in the next `screen`, and that the page buttons change the listed tabs
- [ ] 2.2 Add `walk` and `walk_pos` to `entity` for living entities (D3), with the help fields and README; verify on the 1.21.1 dev client that `sample entity me every 4t while move forward 20t` shows `walk` above 0 with `walk_pos` growing, and that `entity me` standing still shows `walk=0`
- [ ] 2.3 Add `clear` to `camera at` (D4), with the help and README observe row; verify on the 1.21.1 dev client that `camera at` a point inside a stone block with `look` at a point in the air beyond it and `clear` answers `cleared=` with the camera in air, that the same in open air carries no `cleared=`, and that a look point inside the same stone carries `uncleared`
- [ ] 2.4 Add the hints to `place`'s `no_item` and taken-cell errors (D5), with `help errors` unchanged; verify on the 1.21.1 dev client that `place` of an item not in the inventory says to give it first, and that `place stone` into a taken cell with air above names the cell above

## 3. Integration checks

- [ ] 3.1 Run `./gradlew build` for all four targets and the smoke test against the 1.21.1 dev client; verify all pass

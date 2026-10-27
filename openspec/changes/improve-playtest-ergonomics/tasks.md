# Tasks

## 1. Perception (game-perception)

- [x] 1.1 Add `count=<n>` to `find` when the limit cut its list, for blocks, entities and drops (D1), with `help find` (limit default 5, max 100; `count` as matches in all) and the README; verify on the dev client that `find` with eight logs in range and limit 3 carries `count=8` in text and JSON, and limit 10 carries none
- [x] 1.2 Report `width=<n>` from `screenshot` when the frames are narrower than asked (D9), with `help screenshot` (max 1280, for a sheet each tile's width) and the README; verify on the dev client that `screenshot 2560` carries `width=1280`, `screenshot 640` carries none, `screenshot 640 crop=0,0,0.1,0.1` carries the crop's width, and a sheet of 9 frames at 640 carries `width=512`

## 2. Player control (player-control)

- [ ] 2.1 Fail `mine` with the new code `unbreakable` when digging makes no progress, and skip such blocks in `mine <block|#tag> x<n>` (D2), with the code in `help errors`, `help mine` and the README; verify on the dev client that `mine <bedrock>` in survival fails on its first tick, `mine` at bedrock under the crosshair too, `mine bedrock` on a flat world's floor fails `unbreakable bedrock` at once, and bedrock still breaks in creative
- [ ] 2.2 Add the form `key <key> [Nt]` that presses the key (D3), with `help key` and the README; verify on the dev client that `key escape` opens the pause menu from the world, `key up` presses the up arrow, and `key up w` still releases `w`
- [ ] 2.3 Give `PathSearch` the reason a cell can't be stood in, build `standable` on it, and check `goto <pos>`'s target before walking (D4), with unit tests in `PathSearchTest` for each reason, `help goto` and the README; verify on the dev client that `goto` at a stone block names the stone and the cell above, `goto ~0,5,0` says nothing is there to stand on, and neither moves the player

## 3. Observation (client-observation)

- [ ] 3.1 Accept `camera off` as `camera reset` and report `unloaded` for a camera in a chunk the client hasn't loaded (D5), with `help camera` and the README; verify on the dev client that `camera off` returns to first person, and `camera at` 2000 blocks away carries `unloaded` and no block, while one 10 blocks up still names `air`
- [ ] 3.2 Move `option`'s wait into its own `Settle` class and add `settle [timeout=]` (D8), with `help settle` and the README; verify on the dev client that `camera at ~150,0,0; settle` answers `settled=` and a screenshot after it shows the sections around the camera, that `settle` with nothing new in view answers within a few ticks, and that `option renderDistance <n>` still settles

## 4. CLI (dev-integration)

- [ ] 4.1 Say how many seconds the CLI waited in both `--wait-ready` timeout messages (D6), with a unit test in the CLI tests and the README; verify by running the launcher with `--wait-ready 3` against a game directory with no game, and with `--wait-ready 5 --wait-world` against a game in its title screen
- [ ] 4.2 Name the failed steps in the stderr summary (D6), with `ResultsTest` cases for one error, several errors and a syntax error; verify by running a five-step script whose third step fails and reading stderr

## 5. Help notes (sable-sublevels)

- [ ] 5.1 Say in `help sable:list` and `help sable:info` that size and plot bounds grow while a sub-level is turned, and in `help nbt` that `<pos>` may be a plot position (D7), with the README's Sable section; verify the help texts on the dev client and that `nbt <plot pos of a chest aboard>` returns its items

## 6. Integration checks

- [ ] 6.1 Build the four jars and run the smoke test on the 1.21.1 NeoForge dev client; verify all node tests pass and the smoke test passes in text and JSON mode

## Workflow follow-up

- Tell the play-testing session that a new 1.21.1 NeoForge jar is ready, and
  what changed.
- Archive once implemented; no ordering with the three finished changes is
  needed.

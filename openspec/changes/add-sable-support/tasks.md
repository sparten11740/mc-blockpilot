# Tasks

## 1. Optional Sable integration (sable-sublevels: optional, versions, failures)

- [x] 1.1 Add the `sable-companion-common-1.21.1:1.6.0` compile-only dependency and the `maven.ryanhcode.dev` repository (content filtered to `dev.ryanhcode.*`) to both 1.21.1 nodes; verify `./gradlew check` passes on both loaders, the built jars contain no `dev/ryanhcode` classes, and neither metadata file names a `sable` dependency
- [x] 1.2 Add the `SubLevels` interface (sub-level at a position, plot↔world transforms, sub-levels in a box, the one the player rides, info) with its do-nothing default, and a unit test that fails when any source outside `compat/sable` imports `dev.ryanhcode`; verify the test fails with a planted import and passes without
- [x] 1.3 Add the version gate and loader: companion version as the loader reports the `sablecompanion` mod (the `blockpilot.sable.version` property overrides it), 1.x from 1.6 accepted, compat class loaded by name inside a `LinkageError` catch, a probe of the companion's implementation, one log line and a `help sable` explanation when off; verify the version rule with a game-free unit test, and in game that Blockpilot starts without Sable on NeoForge and Fabric with no `sable` namespace in `help`
- [x] 1.4 Guard every compat call: a `LinkageError` fails the command with `err sable …` and switches the integration off until restart, other exceptions fail only the command; verify the switch-off rule with a game-free unit test of the integration state, and in game with `-Dblockpilot.sable.version=2.0.0` that the game starts, logs the version and `help sable` says the integration is off

## 2. Plot positions in replies and arguments (sable-sublevels)

- [x] 2.1 Print plot positions absolute with `sub=<name> world=<x,y,z>` (JSON `sub` and `world` fields) wherever positions are printed, starting with `status`'s aim; verify in a dev client with Sable and a mod that builds sub-levels that aiming at a button aboard prints its plot position and `block` at that position reads the button
- [x] 2.2 Accept plot positions in `block`, `blocks`, `face` and `use`, name the sub-level in `block`'s reply, and refuse `goto` to a plot position with a hint to `sable:tp`; verify in game with Sable that `block <seat>` reports `sub=` and the world position, and `goto <plot position>` fails with the hint

## 3. Aim and camera follow a sub-level (sable-sublevels)

- [x] 3.1 Make `face` and `use` targets at plot positions project through the logical pose every tick, and make `use` fail with `err obstructed <id> <position>` when the crosshair ends on another block; verify in game with Sable: a lever used while the ship moves, and a seat behind a wall panel giving `err obstructed` with the panel
- [x] 3.2 Make the camera placement's position a supplier, and project plot positions for `camera at` and `look` through the render pose each frame; verify in game with Sable that a screenshot burst of a chase camera on a moving ship keeps the ship in place in the frame

## 4. The `sable:` commands (sable-sublevels)

- [x] 4.1 Add sub-level references (UUID, name, `here`; `err ambiguous` with UUIDs, `err no_sublevel`), `sable:list [radius]` and `sable:info <sub-level>` with help, fields and parsing examples; verify in game with Sable next to a ship at rest (speed 0, rides=false), aboard it (`here`, rides=true), and with two sub-levels of one name
- [ ] 4.2 Add `sable:world <plot position>` and `sable:plot <sub-level> <world position>`; verify in game that a round trip returns the starting world position within 0.01 blocks, also in flight
- [ ] 4.3 Add `sable:find <sub-level> <block|#tag|*pattern> [limit]` over the sub-level's plot bounds with palette pruning, the `blocks` cap (`err too_large`) and `side=server`; verify in game that `sable:find here *door* 3` lists the ship's doors with plot and world positions in one call
- [ ] 4.4 Add `sable:tp <plot position>` (server thread, commands allowed, a free spot within two blocks, `err no_room`); verify in game that `sable:tp <captain's seat>` puts the player aboard and `status` shows them riding the ship
- [ ] 4.5 Add a Sable section to the README (what the integration adds, that it needs nothing installed without Sable, the supported versions and those the play-test used) and the `sable:` commands to the command table; verify the README's Sable examples run in a dev client with Sable

## 5. Play-test fixes (player-control, command-language, client-observation, game-perception)

- [ ] 5.1 Accept key names with `_` or `.` between words and list the three closest key names for an unknown one, with help naming the forms; verify with a game-free unit test of the closest-name choice and in game that `key press left_shift 5t` holds shift and `key press leftshift` names `left_shift`
- [ ] 5.2 Let `sample … while` take a `wait` (duration, `idle` or `until`) in the parser, the grammar listing and the runner, and update the core grammar text from the spec; verify with `ParserTest`, `GrammarTest`, an `EngineTest` case (10 rows over `wait 200t` every 20t), and in game
- [ ] 5.3 Name the camera's block in `camera at`'s reply and add `inside=<id>` when it is a full solid block; verify in game with the camera in a stone wall and in the air
- [ ] 5.4 Add `blocks <pos1> <pos2> grid` (one id per cell, rows per layer bottom-up, north to south, west to east); verify in game with the two-by-two scenario in text and JSON
- [ ] 5.5 Print a stderr summary from `tools/blockpilot.py` when lines are `err` or `skip` (counts, and whether nothing ran); verify by running a script with a syntax error (summary on stderr, exit code 1) and a clean one (no summary, exit 0)

## 6. Integration checks

- [ ] 6.1 Without Sable: run `tools/smoke_test.py` in text and JSON mode on NeoForge and Fabric; verify every step passes and the JSON shapes match between the loaders
- [ ] 6.2 With Sable 2.0.6 and a mod that builds sub-levels in a dev client: run the cases end to end (aim on a button aboard, find a door, `sable:tp` onto the bridge, `use` a seat, a chase camera on a moving ship with `sample … while wait`); verify each and record the Sable and companion versions in the README
- [ ] 6.3 Build the jar, share it with the agents play-testing Sable-based mods with what changed, and fold their feedback into the specs or a follow-up; verify they confirm the reported cases work

## Workflow follow-up

- Archive the change once the tasks are done.

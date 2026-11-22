## MODIFIED Requirements

### Requirement: Create a test world
`create world <name> [flat|void|normal] [peaceful|easy|hard|difficulty=<level>]
[mode=creative|survival] [seed=<seed>] [rule:<gamerule>=<value>…]` SHALL
create a singleplayer world with commands allowed, then join it. A bare
`normal` names the world type, so normal difficulty is written
`difficulty=normal`. The result returns once the player can act. Defaults are
`flat`, `peaceful` and `creative`. A name that already exists fails with `err
exists`, unless a creation cut short left it.

#### Scenario: Flat peaceful world
- **WHEN** an agent runs `create world mymod-test flat peaceful
  rule:doDaylightCycle=false`
- **THEN** a flat, peaceful world with commands allowed and the daylight cycle
  off is created and joined, and the result is `ok joined mymod-test`

#### Scenario: Name taken
- **WHEN** a save folder named `mymod-test` already exists
- **THEN** `create world mymod-test` fails with `err exists` and the
  existing world is untouched

### Requirement: Blockpilot-created worlds are marked
Worlds made by `create world` SHALL carry a marker in their save folder,
written before the game starts making the world, so a folder that a creation
cut short leaves carries it too. `worlds` SHALL show the marker as a `test`
flag, and list the world loaded right now first, with a `loaded` flag.

#### Scenario: Worlds listing
- **WHEN** an agent runs `worlds` after creating a test world
- **THEN** that world's line includes `test` and `loaded`

#### Scenario: Deleting a creation cut short
- **WHEN** the game was killed while `create world mymod-test` ran, and an
  agent runs `delete world mymod-test` in the next game
- **THEN** the folder is removed and the result is `ok deleted mymod-test`

### Requirement: Save and restore snapshots
`snapshot save <name>` SHALL copy the current world's whole save folder (all
dimensions, player data and other mods' data) into a named snapshot. `snapshot
restore <name>` SHALL put the world back to that copy. Both save and leave the
world, do the copy, and rejoin, and return once the player can act again.
Snapshot names use letters, digits, `_`, `.`, `+` and `-`, up to 64, and don't
start with `.`; any other name fails with `err syntax` naming it.

#### Scenario: Reset between runs
- **WHEN** an agent runs `snapshot save base`, changes the world, and then runs
  `snapshot restore base`
- **THEN** after the restore, blocks, entities, the player's position and
  inventory, and other mods' saved data match the moment of `snapshot save base`

#### Scenario: Unknown snapshot
- **WHEN** an agent runs `snapshot restore nope`
- **THEN** the result is `err no_snapshot nope`, and the world stays loaded and
  unchanged

## ADDED Requirements

### Requirement: A creation cut short is replaced
`create world <name>` SHALL replace a save folder of that name that carries the
test-world marker and no `level.dat`, which only a creation cut short leaves,
rather than fail with `err exists`. A folder with `level.dat`, or without the
marker, SHALL still fail.

#### Scenario: A creation cut short
- **WHEN** the game was killed while `create world mymod-test` loaded its data
  packs, leaving a marked `mymod-test` folder without `level.dat`, and an
  agent runs `create world mymod-test` in the next game
- **THEN** the folder is replaced by a new world, and the result is `ok joined
  mymod-test`

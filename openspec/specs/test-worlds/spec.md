# test-worlds Specification

## Purpose

Lets agents create disposable singleplayer test worlds and reset them to a saved
state between runs. Experiments then start from the same world every time,
including other mods' saved data.

## Requirements

### Requirement: Create a test world
`create world <name> [flat|void|normal] [peaceful|easy|hard|difficulty=<level>]
[mode=creative|survival] [seed=<seed>] [rule:<gamerule>=<value>…]` SHALL create
a singleplayer world with commands allowed, then join it. A bare `normal` names
the world type, so normal difficulty is written `difficulty=normal`. The result
returns once the player can act. Defaults are `flat`, `peaceful` and
`creative`. A name that already exists fails with `err exists`.

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
Worlds made by `create world` SHALL carry a marker in their save folder.
`worlds` SHALL show the marker as a `test` flag, and list the world loaded right
now first, with a `loaded` flag.

#### Scenario: Worlds listing
- **WHEN** an agent runs `worlds` after creating a test world
- **THEN** that world's line includes `test` and `loaded`

### Requirement: Save and restore snapshots
`snapshot save <name>` SHALL copy the current world's whole save folder (all
dimensions, player data and other mods' data) into a named snapshot. `snapshot
restore <name>` SHALL put the world back to that copy. Both save and leave the
world, do the copy, and rejoin, and return once the player can act again.

#### Scenario: Reset between runs
- **WHEN** an agent runs `snapshot save base`, changes the world, and then runs
  `snapshot restore base`
- **THEN** after the restore, blocks, entities, the player's position and
  inventory, and other mods' saved data match the moment of `snapshot save base`

#### Scenario: Unknown snapshot
- **WHEN** an agent runs `snapshot restore nope`
- **THEN** the result is `err no_snapshot nope`, and the world stays loaded and
  unchanged

### Requirement: Snapshots belong to their world
Snapshots SHALL be stored per world, outside the save folder. `snapshot list`
SHALL list the current world's snapshots with creation time and size, and
`snapshot delete <name>` SHALL remove one. Restoring a snapshot into a different
world is not possible.

#### Scenario: List snapshots
- **WHEN** a world has snapshots `base` and `after-build`
- **THEN** `snapshot list` returns both as rows with time and size

### Requirement: Snapshots need the cheats gate
`snapshot` (every subcommand) SHALL follow the server-bridge gate, so it is
available only in singleplayer worlds that allow commands.

#### Scenario: Survival world
- **WHEN** an agent runs `snapshot save x` in a survival world without commands
- **THEN** the result is `err no_cheats`, and nothing is copied

### Requirement: Delete only test worlds
`delete world <folder>` SHALL delete a save only if it carries the Blockpilot
test-world marker and isn't loaded. Any other save fails with `err
not_test_world`, and a loaded one with `err syntax` asking to leave first.

#### Scenario: Delete a user's world
- **WHEN** an agent runs `delete world New World` on a world the user created
- **THEN** the result is `err not_test_world`, and the save is untouched

#### Scenario: Delete a test world
- **WHEN** an agent runs `leave; delete world mymod-test`
- **THEN** the test world's save folder and its snapshots are removed

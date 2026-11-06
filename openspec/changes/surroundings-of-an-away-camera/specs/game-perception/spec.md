## ADDED Requirements

### Requirement: Surroundings of an away camera
While a camera is placed with `away`, the surroundings `look` and `find`
search SHALL be the camera's place, where the server takes the player to be,
and they SHALL sort by distance from there. Positions in their rows SHALL
stay relative to the player's block, and `~` in their arguments relative to
the player.

#### Scenario: Looking around the set
- **WHEN** an agent runs `camera at 1000,-58,0 look 1000,-60,5 away; settle;
  server summon minecraft:armor_stand 1003 -60 3; wait 5t; look 8`
- **THEN** `look` lists the armor stand, at its position relative to the
  player's block

#### Scenario: Finding on the set
- **WHEN** an agent runs `find armor_stand 8; find grass_block 3 1` with that
  camera placed
- **THEN** the armor stand is the first row of the first `find`, and the
  second names the grass block `1000,-61,0` under the camera as `at_abs`

#### Scenario: Back to the player
- **WHEN** an agent runs `camera reset; settle; look 8`
- **THEN** `look` lists what is around the player, without the armor stand

## MODIFIED Requirements

### Requirement: Look at surroundings
`look [radius]` (default 16, max 64) SHALL list the entities around the
player, or a camera placed with `away`, (type, reference, relative position,
health for living ones, held item when present) sorted by distance, and the
notable blocks within the radius: blocks with a block entity (containers,
machines, modded ones too) and blocks on a configurable notable list
(default: ores, liquids, crafting stations, beds, doors), grouped by type
with count and nearest relative position.

#### Scenario: Modded machine nearby
- **WHEN** a modded block with a block entity (e.g. a Create mechanical press)
  is within the radius
- **THEN** it is listed with its namespaced id, without any configuration

#### Scenario: Cave surroundings
- **WHEN** an agent runs `look 12` in a cave with two zombies and some iron ore
- **THEN** the result lists both zombies with `e<id>`, relative position and
  health
- **AND** a line like `iron_ore 5 near ~3,-1,2`

### Requirement: Find
`find <block|item|entity-type|#tag> [radius] [limit]` SHALL return the nearest
matches (default limit 5, max radius 128) as positions or entity references,
nearest first. Results only include chunks the client has loaded. The radius
reaches that many blocks each way, a cube, from the player, or a camera
placed with `away`. A radius over the maximum SHALL be cut to it, and the
result then carries `radius=128`.

#### Scenario: Find nearest logs
- **WHEN** an agent runs `find #logs 32 3`
- **THEN** up to three positions of log blocks are returned, nearest first

#### Scenario: Nothing found
- **WHEN** no matching block is loaded within the radius
- **THEN** the result is `ok none`

#### Scenario: Radius over the maximum
- **WHEN** an agent runs `find #logs 400`
- **THEN** the result carries `radius=128` and lists the logs within 128
  blocks

#### Scenario: Positions in JSON
- **WHEN** an agent runs `find stone 8 1` in JSON mode
- **THEN** the row carries `at`, relative to the player's block as the find
  ran, and `at_abs`, the same position absolute

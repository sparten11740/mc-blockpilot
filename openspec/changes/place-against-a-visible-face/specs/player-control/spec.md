## ADDED Requirements

### Requirement: Place picks a neighbour in sight
`place` SHALL go through the target cell's neighbours that can't be replaced
in a fixed order: below, above, north, south, west, east. It SHALL place
against the first whose face toward the target cell is in sight: the first
block face on the line from the player's eyes to where `place` aims, within
reach. With none in sight, it SHALL aim at the first and fail as it does when
that face is hidden. A cell aboard a sub-level SHALL keep the first neighbour.

#### Scenario: Filling a hole
- **WHEN** an agent standing on flat ground runs `place cobblestone ~0,-1,2`
  where `~0,-1,2` is a hole one block deep
- **THEN** the cobblestone fills the hole, placed against its far side: the
  hole's floor is hidden behind the ground in front of it, the cell above is
  air, and the near side faces away, so the far side, south in the order,
  is the first in sight

#### Scenario: Above the eyes, beside a block
- **WHEN** an agent standing on flat ground, not sneaking, runs `place
  cobblestone ~2,2,0` with stone at `~2,0,0`, `~2,1,0` and `~2,2,-1`
- **THEN** the cobblestone is placed at `~2,2,0` against the stone at
  `~2,2,-1`

#### Scenario: The ground before a wall
- **WHEN** an agent standing on flat ground runs `place torch ~2,0,0` with
  stone at `~3,0,0`
- **THEN** the torch stands on the ground, not on the stone's side

#### Scenario: No neighbour in sight
- **WHEN** an agent runs `place cobblestone ~3,2,0` with stone at `~2,2,0` and
  `~4,2,0` and nothing else next to `~3,2,0`
- **THEN** the result is `err out_of_reach` naming `stone ~2,2,0` on its west
  face, and no cobblestone is placed

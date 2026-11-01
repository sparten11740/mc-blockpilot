## ADDED Requirements

### Requirement: Place aims at the support's shape
`place` SHALL aim at the centre of the supporting block's shape on the side
facing the target cell, not at the face of the supporting block's cell, so a
block can be placed against a thin or narrow block such as a carpet, a slab
or a fence. It SHALL click only when the crosshair hits that side; when it
hits another block or another side, it SHALL fail with `err out_of_reach` and
place nothing.

#### Scenario: On a carpet
- **WHEN** an agent runs `place cobblestone ~2,1,0` with a carpet at `~2,0,0`
  and air above it
- **THEN** the cobblestone stands on the carpet

#### Scenario: Against a fence
- **WHEN** an agent runs `place cobblestone ~2,1,1` with a fence post at
  `~2,1,2` and nothing else next to `~2,1,1`
- **THEN** the cobblestone is placed against the fence

#### Scenario: A side out of sight
- **WHEN** an agent runs `place cobblestone ~3,2,0` with stone at `~2,2,0`
  and nothing else next to `~3,2,0`
- **THEN** the result is `err out_of_reach` and no cobblestone is placed

### Requirement: Blocked-view errors name what is in the way
When `mine <pos>` fails because its block is behind another, and when `place`
can't see the face it places against, the error SHALL name what the crosshair
hits instead: the block and its position, the entity and its reference, or
nothing.

#### Scenario: A block behind another
- **WHEN** an agent runs `mine ~3,0,0` with stone at `~2,0,0` in the way
- **THEN** the result is `err out_of_reach ~3,0,0 is behind stone ~2,0,0`

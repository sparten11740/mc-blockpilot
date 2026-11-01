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

### Requirement: Mine aims at the block's shape
`mine <pos>` and `mine` by block SHALL aim at the centre of the block's shape,
as `use` does, not at the centre of its cell, so a thin block such as a
carpet, a rail or a pressure plate is mined rather than missed.

#### Scenario: A carpet by position
- **WHEN** an agent runs `mine ~0,0,2` with a carpet at `~0,0,2` on flat
  ground
- **THEN** the carpet is mined, and the result is `ok mine 1/1 white_carpet`

### Requirement: Blocked-view errors name what is in the way
When `mine <pos>` fails because its block is behind another, and when `place`
can't see the face it places against, the error SHALL name what the crosshair
hits instead: the block and its position, the entity and its reference, or
nothing.

#### Scenario: A block behind another
- **WHEN** an agent runs `mine ~3,0,0` with stone at `~2,0,0` in the way
- **THEN** the result is `err out_of_reach ~3,0,0 is behind stone ~2,0,0`

## MODIFIED Requirements

### Requirement: Block interaction
The mod SHALL provide `mine <pos>`, `mine <block|#tag> x<n>` (find, walk to and
mine the n nearest), `place <item> <pos>` (select the item and place it
against a neighbouring block), and `use [<pos>|e<id>]` (right-click a block,
an entity, or the held item). Targets out of reach without a walk fail with `err
out_of_reach`.

#### Scenario: Mine several logs
- **WHEN** an agent runs `mine #logs x6`
- **THEN** the player walks to and mines up to six log blocks, and the result
  reports `ok mine 6/6 oak_log`

#### Scenario: Place without item
- **WHEN** an agent runs `place torch ~1,0,0` with no torch in the inventory
- **THEN** the result is `err no_item torch`

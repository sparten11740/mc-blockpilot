## MODIFIED Requirements

### Requirement: Single block query
`block <pos>` SHALL return the block id, each of its state properties with its
value, default values included, and its block-entity type when it has one, as
the fields `id`, `props` and `be`. A position in a chunk the client hasn't
loaded fails with `err unloaded`.

#### Scenario: Block with state and block entity
- **WHEN** an agent runs `block ~1,0,0` on an east-facing single chest
- **THEN** the result is `ok chest facing=east type=single waterlogged=false
  be:chest`

#### Scenario: Plain block
- **WHEN** an agent runs `block ~0,-1,0` while standing on stone
- **THEN** the result is `ok stone`

#### Scenario: A property at its default value
- **WHEN** an agent runs `assert block <pos> props.lit==false` on an unlit
  redstone lamp
- **THEN** the result is `ok props.lit=false`

### Requirement: Find
`find <block|item|entity-type|#tag> [radius] [limit]` SHALL return the nearest
matches (default limit 5, max radius 128) as positions or entity references,
nearest first. Results only include chunks the client has loaded. The radius
reaches that many blocks each way from the player, a cube. A radius over the
maximum SHALL be cut to it, and the result then carries `radius=128`.

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

## ADDED Requirements

### Requirement: Creative inventory tabs
While the creative inventory is open, `screen` SHALL list the tabs shown on its
current page as rows `t<i>:tab "<name>" @x,y,w,h`, numbered from 0 in the order
the page holds them, with each tab's GUI-scaled bounds and `selected` on the
open tab. Tabs on other pages are left out until the page is turned with the
screen's page buttons.

#### Scenario: Opening a mod's tab
- **WHEN** the creative inventory is open on the page with a mod's tab, and an
  agent runs `screen` and then clicks the centre of that tab's bounds
- **THEN** the tab opens, and the next `screen` lists it with `selected`

### Requirement: Walk animation in the entity query
For a living entity, `entity` SHALL add its walk animation: `walk=<speed>`, how
fast its limbs swing, and `walk_pos=<position>`, how far through their swing
they are, each to the thousandth. Other entities carry neither field.

#### Scenario: Walking and standing
- **WHEN** an agent samples `entity me` while the player walks forward and
  then stands still
- **THEN** `walk` is above 0 and `walk_pos` grows while the player walks, and
  `walk` falls back to 0 once the player stands

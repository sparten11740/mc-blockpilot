# game-perception Specification

## Purpose

Read-only commands that tell an agent what the player can perceive: own state,
surroundings, inventory, the open screen, and a picture when text is not enough.
All output is compact text, except the screenshot.

## Requirements

### Requirement: Status
`status` SHALL report on one line: position, facing (yaw/pitch), dimension, time
of day, health/max, food, XP level, held item, and the crosshair target (block
with position, face and `hit=<u,v,w>`, the point the crosshair meets in the
block's own frame as `face`/`use … at` take it, or entity reference).
Non-default fields are added: effects, air, armor, vehicle, on-fire, in-water,
sleeping, open-screen type.

#### Scenario: Status in the overworld
- **WHEN** an agent runs `status` while standing in the overworld holding an
  iron pickaxe and aiming at stone
- **THEN** the result is one line like `ok pos 104.5,64,-33.2 yaw90 pitch20
  overworld daytime6000 hp20/20 food18 lv3 hand:iron_pickaxe aim:stone ~1,0,0
  face:west hit=0,0.62,0.48`

#### Scenario: Not in a world
- **WHEN** an agent runs `status` from the title screen
- **THEN** the result is `ok menu <screen type>`

### Requirement: Look at surroundings
`look [radius]` (default 16, max 64) SHALL list nearby entities (type,
reference, relative position, health for living ones, held item when present)
sorted by distance. It SHALL also list notable blocks within the radius: blocks
with a block entity (containers, machines, including modded ones) and blocks on
a user-configurable notable list (default: ores, liquids, crafting stations,
beds, doors). Notable blocks are grouped by type with count and nearest relative
position.

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

### Requirement: Single block query
`block <pos>` SHALL return the block id, its non-default state properties and
its block-entity type when it has one, as the fields `id`, `props` and `be`. A
position in a chunk the client hasn't loaded fails with `err unloaded`.

#### Scenario: Block with state and block entity
- **WHEN** an agent runs `block ~1,0,0` on an east-facing chest
- **THEN** the result is `ok chest facing=east be:chest`

#### Scenario: Plain block
- **WHEN** an agent runs `block ~0,-1,0` while standing on stone
- **THEN** the result is `ok stone`

### Requirement: Region block dump
`blocks <pos1> <pos2>` SHALL dump the cuboid between two positions (at most 4096
blocks). The output has a palette line that maps short symbols to block states,
then one line per Y layer, bottom to top, with run-length encoding. Air prints
as `.`, and blocks in chunks that aren't loaded as `?`.

#### Scenario: Small region
- **WHEN** an agent runs `blocks ~-2,-1,-2 ~2,0,2`
- **THEN** the result has a palette and two Y-layer sections of 5 rows each,
  row-major from north-west

#### Scenario: Region too large
- **WHEN** the requested region contains more than 4096 blocks
- **THEN** the result is `err too_large 4096`

### Requirement: Inventory
`inv` SHALL list non-empty slots as `<slot>:<item> x<count>`, marking the
selected hotbar slot. Durability is shown only for damaged items, and
enchantments or custom names only with `inv detail`. Armor and offhand are
listed under their slot names.

#### Scenario: Compact inventory
- **WHEN** the player holds 32 cobblestone in hotbar slot 0 (selected) and a
  damaged iron pickaxe in slot 1
- **THEN** the payload includes `*0:cobblestone x32` and `1:iron_pickaxe
  dur120/250`

### Requirement: Open screen description
`screen` SHALL describe the open GUI: its type and title, container slots with
contents (`s<index>:<item> x<count>`), and interactive widgets (buttons, text
fields, sliders, toggles) with label, state and GUI-scaled bounds `x,y,w,h`. It
also reports the GUI scale, the screen size and the cursor position, so pointer
commands can target any widget, including unlabelled modded widgets.

#### Scenario: Crafting table open
- **WHEN** a crafting table screen is open
- **THEN** `screen` lists the grid, result and inventory slots with their
  contents

#### Scenario: Modded screen with custom widgets
- **WHEN** a modded screen with a slider and an unlabelled icon button is open
- **THEN** both are listed with their kind and bounds, and the icon button with
  its tooltip text when it has one

### Requirement: Rows of lists on screens
`screen` SHALL list a list widget (an options list, the controls, a world or
mod list) with its row count, followed by the rows in view as widgets of their
own: a row's own widgets, or the row itself with its label.

#### Scenario: Rows of the video settings
- **WHEN** Video Settings is open
- **THEN** `screen` lists the options list and, as their own `w<i>`, the
  sliders and buttons of its rows in view, which `click w<i>` reaches

### Requirement: Screenshot
`screenshot [width]` (MCP tool `screenshot`) SHALL capture the next rendered
frame, downscaled to the requested width (default 512, max 1280) and
PNG-encoded. Over MCP it returns image content. Over the line protocol it writes
the image to the mod's screenshot directory and returns the file path and the
tick it captured the frame on, as `t<tick>`, as burst frames do.

#### Scenario: MCP screenshot
- **WHEN** an agent calls the `screenshot` tool with width 640
- **THEN** the result is a PNG image 640 px wide with the game's aspect ratio

#### Scenario: Line screenshot
- **WHEN** a line client sends `screenshot`
- **THEN** the reply is `ok <absolute file path> t<tick>`

#### Scenario: Screenshot after a click
- **WHEN** an agent runs `click w3; screenshot` on a screen that redraws when
  it next ticks
- **THEN** the image shows the screen as the click left it, not the frame
  rendered before the screenshot began

### Requirement: Observation never changes game state
Perception commands SHALL NOT move the player, change the selected slot, send
packets to the server, or modify the world.

#### Scenario: Look does not turn the player
- **WHEN** an agent runs `look` and `find`
- **THEN** the player's yaw, pitch, position and selected slot are unchanged

### Requirement: Region dump as a grid
`blocks <pos1> <pos2> grid` SHALL dump the same cuboid with the same palette
line, then one palette key per cell instead of run lengths: one line per row,
grouped by Y layer from the bottom, each row north to south and each cell west
to east, cells separated by a space, air as `.` and blocks in chunks that
aren't loaded as `?`.

#### Scenario: Two by two
- **WHEN** an agent runs `blocks ~0,-1,0 ~1,-1,1 grid` on grass with one stone
- **THEN** the reply has a palette naming `grass_block` and `stone`, then one
  layer of two rows of two keys, with stone's key in the cell where the stone
  is

#### Scenario: Long modded ids
- **WHEN** a region holds blocks with long namespaced ids
- **THEN** each id appears once, in the palette, and every row has one short
  key per cell

### Requirement: Find counts what its limit left out
`find`'s limit SHALL be at most 100, and a larger one is cut to 100. When more
matches are found than the limit lets it list, the result SHALL carry
`count=<n>`, the number of matches in all; when it lists them all, it carries
no `count`.

#### Scenario: More matches than the limit
- **WHEN** an agent runs `find #logs 32 3` with eight logs loaded within 32
  blocks
- **THEN** the three nearest logs are listed and the result carries `count=8`

#### Scenario: All matches listed
- **WHEN** an agent runs `find #logs 32 10` with eight logs loaded within 32
  blocks
- **THEN** the eight logs are listed and the result carries no `count`

### Requirement: Screenshot width as captured
`screenshot`'s width SHALL be at most 1280, and, down to 16 pixels, never
more than the frame's or the crop's own width; with `sheet` it is each tile's
width. When the image is narrower than the width asked for, or than 512 when
none is, the reply SHALL carry `width=<n>`, the width it has; otherwise it
carries no `width`.

#### Scenario: Width over the cap
- **WHEN** an agent runs `screenshot 2560` with frames 2560 pixels wide
- **THEN** the image is 1280 pixels wide and the reply carries `width=1280`

#### Scenario: Width as asked
- **WHEN** an agent runs `screenshot 640` with frames 1280 pixels wide
- **THEN** the image is 640 pixels wide and the reply carries no `width`

### Requirement: Entity query
`entity <e<id>>` SHALL answer one entity's motion as the client has it: its
type, absolute position to the thousandth of a block, yaw and pitch to the
tenth of a degree, velocity in blocks per tick to the thousandth, and
`airborne` when it doesn't stand on the ground. An entity the client doesn't
hold SHALL fail with `err no_entity`.

#### Scenario: A thrown item on the client
- **WHEN** an agent runs `entity e5` for an item the player has just thrown
- **THEN** the reply is like `ok item pos 12.304,65.871,-3.502 yaw12.5 pitch0
  vel 0.112,0.207,-0.031 airborne`

#### Scenario: Gone
- **WHEN** an agent runs `entity e5` after the item was picked up
- **THEN** the result is `err no_entity e5`

### Requirement: Entity query on the server
`entity <e<id>> side=server` SHALL answer the same fields as the integrated
server has the entity, adding its UUID. Like other `side=server` reads it
needs cheats on, and an entity the server doesn't hold SHALL fail with
`err no_entity`.

#### Scenario: The server's copy
- **WHEN** an agent runs `entity e5 side=server` in singleplayer with cheats
  on
- **THEN** the reply gives the same fields as the integrated server has the
  entity, and its `uuid`

#### Scenario: Both sides sampled
- **WHEN** an agent runs `sample entity e5 and entity e5 side=server every 1t
  x20` while the item flies
- **THEN** each of the twenty ticks gives a client row and a server row with
  the same tick, so their positions can be compared

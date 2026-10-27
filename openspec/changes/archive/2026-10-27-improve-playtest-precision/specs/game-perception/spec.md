# Spec Delta

## MODIFIED Requirements

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

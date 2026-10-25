# Spec Delta

## MODIFIED Requirements

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

### Requirement: Screenshot
`screenshot [width]` (MCP tool `screenshot`) SHALL capture the next rendered
frame, downscaled to the requested width (default 512, max 1280) and
PNG-encoded. Over MCP it returns image content. Over the line protocol it writes
the image to the mod's screenshot directory and returns the file path.

#### Scenario: MCP screenshot
- **WHEN** an agent calls the `screenshot` tool with width 640
- **THEN** the result is a PNG image 640 px wide with the game's aspect ratio

#### Scenario: Line screenshot
- **WHEN** a line client sends `screenshot`
- **THEN** the reply is `ok <absolute file path>`

#### Scenario: Screenshot after a click
- **WHEN** an agent runs `click w3; screenshot` on a screen that redraws when
  it next ticks
- **THEN** the image shows the screen as the click left it, not the frame
  rendered before the screenshot began

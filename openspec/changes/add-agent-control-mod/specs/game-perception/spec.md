# Spec Delta

## Purpose

Read-only commands that tell an agent what the player can perceive: own state, surroundings, inventory, the open screen, and a picture when text is not enough. All output is compact text, except the screenshot.

## ADDED Requirements

### Requirement: Status
`status` SHALL report on one line: position, facing (yaw/pitch), dimension, time of day, health/max, food, XP level, held item, and the crosshair target (block with position and face, or entity reference). Non-default fields are added: effects, air, armor, vehicle, on-fire, in-water, sleeping, open-screen type.

#### Scenario: Status in the overworld
- **WHEN** an agent runs `status` while standing in the overworld holding an iron pickaxe and aiming at stone
- **THEN** the result is one line like `ok pos 104.5,64,-33.2 yaw90 pitch20 overworld t6000 hp20/20 food18 lv3 hand:iron_pickaxe aim:stone ~1,0,0 face:west`

#### Scenario: Not in a world
- **WHEN** an agent runs `status` from the title screen
- **THEN** the result is `ok menu <screen type>`

### Requirement: Look at surroundings
`look [radius]` (default 16, max 64) SHALL list nearby entities (type, reference, relative position, health for living ones, held item when present) sorted by distance. It SHALL also list notable blocks within the radius: blocks with a block entity (containers, machines, including modded ones) and blocks on a user-configurable notable list (default: ores, liquids, crafting stations, beds, doors). Notable blocks are grouped by type with count and nearest relative position.

#### Scenario: Modded machine nearby
- **WHEN** a modded block with a block entity (e.g. a Create mechanical press) is within the radius
- **THEN** it is listed with its namespaced id, without any configuration

#### Scenario: Cave surroundings
- **WHEN** an agent runs `look 12` in a cave with two zombies and some iron ore
- **THEN** the result lists both zombies with `e<id>`, relative position and health
- **AND** a line like `iron_ore 5 near ~3,-1,2`

### Requirement: Find
`find <block|item|entity-type|#tag> [radius] [limit]` SHALL return the nearest matches (default limit 5, max radius 128) as positions or entity references, nearest first. Results only include chunks the client has loaded.

#### Scenario: Find nearest logs
- **WHEN** an agent runs `find #logs 32 3`
- **THEN** up to three positions of log blocks are returned, nearest first

#### Scenario: Nothing found
- **WHEN** no matching block is loaded within the radius
- **THEN** the result is `ok none`

### Requirement: Single block query
`block <pos>` SHALL return the block id, its non-default state properties and its block-entity type when it has one, as the fields `id`, `props` and `be`.

#### Scenario: Block with state and block entity
- **WHEN** an agent runs `block ~1,0,0` on an east-facing chest
- **THEN** the result is `ok chest facing=east be:chest`

#### Scenario: Plain block
- **WHEN** an agent runs `block ~0,-1,0` while standing on stone
- **THEN** the result is `ok stone`

### Requirement: Region block dump
`blocks <pos1> <pos2>` SHALL dump the cuboid between two positions (at most 4096 blocks). The output has a palette line that maps short symbols to block states, then one line per Y layer, bottom to top, with run-length encoding. Air prints as `.`.

#### Scenario: Small region
- **WHEN** an agent runs `blocks ~-2,-1,-2 ~2,0,2`
- **THEN** the result has a palette and two Y-layer sections of 5 rows each, row-major from north-west

#### Scenario: Region too large
- **WHEN** the requested region contains more than 4096 blocks
- **THEN** the result is `err too_large 4096`

### Requirement: Inventory
`inv` SHALL list non-empty slots as `<slot>:<item> x<count>`, marking the selected hotbar slot. Durability is shown only for damaged items, and enchantments or custom names only with `inv detail`. Armor and offhand are listed under their slot names.

#### Scenario: Compact inventory
- **WHEN** the player holds 32 cobblestone in hotbar slot 0 (selected) and a damaged iron pickaxe in slot 1
- **THEN** the payload includes `*0:cobblestone x32` and `1:iron_pickaxe dur120/250`

### Requirement: Open screen description
`screen` SHALL describe the open GUI: its type and title, container slots with contents (`s<index>:<item> x<count>`), and interactive widgets (buttons, text fields, sliders, toggles) with label, state and GUI-scaled bounds `x,y,w,h`. It also reports the GUI scale, the screen size and the cursor position, so pointer commands can target any widget, including unlabelled modded widgets.

#### Scenario: Crafting table open
- **WHEN** a crafting table screen is open
- **THEN** `screen` lists the grid, result and inventory slots with their contents

#### Scenario: Modded screen with custom widgets
- **WHEN** a modded screen with a slider and an unlabelled icon button is open
- **THEN** both are listed with their kind and bounds, and the icon button with its tooltip text when it has one

### Requirement: Screenshot
`screenshot [width]` (MCP tool `screenshot`) SHALL capture the next rendered frame, downscaled to the requested width (default 512, max 1280) and PNG-encoded. Over MCP it returns image content. Over the line protocol it writes the image to the mod's screenshot directory and returns the file path.

#### Scenario: MCP screenshot
- **WHEN** an agent calls the `screenshot` tool with width 640
- **THEN** the result is a PNG image 640 px wide with the game's aspect ratio

#### Scenario: Line screenshot
- **WHEN** a line client sends `screenshot`
- **THEN** the reply is `ok <absolute file path>`

### Requirement: Observation never changes game state
Perception commands SHALL NOT move the player, change the selected slot, send packets to the server, or modify the world.

#### Scenario: Look does not turn the player
- **WHEN** an agent runs `look` and `find`
- **THEN** the player's yaw, pitch, position and selected slot are unchanged

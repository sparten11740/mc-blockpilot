# Spec Delta

## ADDED Requirements

### Requirement: Mining a block that can't be broken
When the player can't break the block `mine` is about to dig (it would make no
progress: bedrock outside creative, a block adventure mode forbids, a block a
mod makes unbreakable), `mine <pos>` and `mine` at the crosshair SHALL fail at
once with `err unbreakable <block> <pos>` instead of digging until a timeout.
`mine <block|#tag> x<n>` SHALL pass such blocks over, and fail at once with
`err unbreakable <block>` when it finds matching blocks but none it can break.

#### Scenario: Bedrock in survival
- **WHEN** an agent in survival runs `mine <pos of bedrock in reach>`
- **THEN** the result is `err unbreakable bedrock <pos>` on the first tick

#### Scenario: A floor of bedrock
- **WHEN** an agent in survival runs `mine bedrock` in a flat world
- **THEN** the result is `err unbreakable bedrock` at once

#### Scenario: The same block in creative
- **WHEN** an agent in creative runs `mine <pos of bedrock in reach>`
- **THEN** the bedrock is broken, as creative breaks any block

### Requirement: A key alone is pressed
`key <key> [Nt]` without `down`, `up` or `press` SHALL press the key, the same
as `key press <key> [Nt]`. The words `up` and `down` alone name the arrow
keys.

#### Scenario: Escape
- **WHEN** an agent runs `key escape`
- **THEN** escape is pressed for one tick, as with `key press escape`

#### Scenario: Arrow key
- **WHEN** an agent runs `key up`
- **THEN** the up arrow key is pressed

### Requirement: Goto checks its target cell
`goto <pos>` without `near=` SHALL fail at once with `err no_path` and the
reason when the player can't stand in the target cell: a block fills it or the
cell above, nothing below holds the player up, it is dangerous, or its chunk
isn't loaded. When the target is a block with room to stand on top, the error
SHALL name the cell above. Water and climbable cells count as cells to stand
in.

#### Scenario: Target is a block to stand on
- **WHEN** an agent runs `goto <pos of a stone block>` with air above it
- **THEN** the result is `err no_path` naming the stone, and the cell above it
  as the one to walk to, without the player moving

#### Scenario: Target in mid-air
- **WHEN** an agent runs `goto ~0,5,0` with nothing under that cell
- **THEN** the result is `err no_path` saying nothing is there to stand on

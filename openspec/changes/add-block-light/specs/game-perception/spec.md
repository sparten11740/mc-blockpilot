## ADDED Requirements

### Requirement: Light in the block query
`block <pos> light` SHALL add the light the level stores for that cell as
`sky=<0-15>` and `block_light=<0-15>`, sky light before the time of day dims
it. With `side=server` both come from the integrated server's level, as
`block`'s other fields do. Without `light` the reply carries neither.

#### Scenario: Open air in daylight
- **WHEN** an agent runs `block ~0,0,0 light` standing in the open
- **THEN** the reply carries `sky=15 block_light=0`

#### Scenario: Next to a torch
- **WHEN** an agent runs `block <the cell beside a torch, under a roof> light`
- **THEN** the reply carries `block_light=13`

#### Scenario: Without light
- **WHEN** an agent runs `block ~0,-1,0` standing on stone
- **THEN** the result is `ok stone`, with no `sky=` or `block_light=`

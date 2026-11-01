## ADDED Requirements

### Requirement: Light at a plot position
For a plot position, `block <pos> light` SHALL give the plot's own light for
that cell as `sky=` and `block_light=`, and add `world_sky=` and
`world_block_light=`: the light at the world cell where the block's centre is
drawn now. The world fields SHALL be left out while that world cell's chunk
isn't loaded.

#### Scenario: A block aboard and its place in the world
- **WHEN** an agent runs `block <plot position of a slab aboard a ship> light`
- **THEN** the reply carries `sky=`, `block_light=`, `world_sky=` and
  `world_block_light=`, each from 0 to 15

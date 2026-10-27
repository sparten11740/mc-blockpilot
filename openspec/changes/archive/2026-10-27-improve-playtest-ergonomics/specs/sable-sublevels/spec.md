# Spec Delta

## MODIFIED Requirements

### Requirement: Listing sub-levels
`sable:list [radius]` SHALL list the sub-levels the client knows within the
radius (default 128 blocks), nearest first. Each row gives the name, UUID,
world position, distance, speed and size. `sable:info <sub-level>` SHALL give
one sub-level's world position, heading, pitch and roll, velocity, world and
plot bounds, and whether the player rides it. Their help SHALL say that the
size and plot bounds are the world bounds turned back into the plot, so they
grow while the sub-level is turned.

#### Scenario: Ship nearby
- **WHEN** an agent runs `sable:list` next to a ship at rest
- **THEN** a row names it with its UUID, world position and speed 0

#### Scenario: Turned ship
- **WHEN** an agent runs `sable:list` while a ship that is 40 blocks long when
  level is pitched by 30°
- **THEN** its row's size is larger than when it was level, as `help
  sable:list` explains

## MODIFIED Requirements

### Requirement: Height above ground
`sable:info` SHALL give a sub-level's height above ground as `agl=<blocks>`:
from its lowest point as it lies now, the lowest corner of the block aboard
that lies lowest in the world, down to the top of the highest block with a
collision shape straight under that point, not counting blocks aboard any
sub-level. With nothing below in loaded chunks, or none of its blocks held by
the client, it SHALL give `agl=none`.

#### Scenario: Ship parked on the ground
- **WHEN** an agent runs `sable:info <ship>` for a ship resting on grass
- **THEN** the reply carries `agl=0`

#### Scenario: Ship in flight
- **WHEN** a ship hovers with its lowest block 30 blocks above the terrain
- **THEN** `sable:info <ship>` carries `agl=30`

#### Scenario: Ship banked in flight
- **WHEN** a wide ship banks 30° while the lowest corner of its low wingtip
  lies 20 blocks above flat ground
- **THEN** `sable:info <ship>` carries `agl=20`, though the bottom of its world
  bounds lies lower

### Requirement: Flight readout
`sable:info <sub-level> brief` SHALL answer with heading, pitch and roll in
degrees, speed and climb rate in blocks per second, the world y of its
position and the height above ground, and nothing else, so
`sample sable:info <sub-level> brief every <Nt>` traces a take-off or a climb
in short rows.

#### Scenario: Climb trace
- **WHEN** an agent runs `sample sable:info ship brief every 10t x6` while a
  ship climbs
- **THEN** each of the six rows gives `yaw`, `pitch`, `roll`, `speed`,
  `climb`, `y` and `agl`, and `y` and `agl` grow from row to row

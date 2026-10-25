# Spec Delta

## ADDED Requirements

### Requirement: Height above ground
`sable:info` SHALL give a sub-level's height above ground as `agl=<blocks>`:
from the bottom of its world bounds down to the top of the highest block with a
collision shape under the centre of those bounds, not counting blocks aboard
any sub-level. With nothing below in loaded chunks it SHALL give `agl=none`.

#### Scenario: Ship parked on the ground
- **WHEN** an agent runs `sable:info <ship>` for a ship resting on grass
- **THEN** the reply carries `agl=0`

#### Scenario: Ship in flight
- **WHEN** a ship hovers with its lowest block 30 blocks above the terrain
- **THEN** `sable:info <ship>` carries `agl=30`

### Requirement: Flight readout
`sable:info <sub-level> brief` SHALL answer with heading, pitch and roll in
degrees, speed and climb rate in blocks per second, and the height above
ground, and nothing else, so `sample sable:info <sub-level> brief every <Nt>`
traces a take-off or a climb in short rows.

#### Scenario: Climb trace
- **WHEN** an agent runs `sample sable:info ship brief every 10t x6` while a
  ship climbs
- **THEN** each of the six rows gives `yaw`, `pitch`, `roll`, `speed`, `climb`
  and `agl`, and `agl` grows from row to row

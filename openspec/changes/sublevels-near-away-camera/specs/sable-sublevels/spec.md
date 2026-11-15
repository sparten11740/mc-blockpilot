## ADDED Requirements

### Requirement: Sub-levels round a camera away
While a camera is placed with `away`, the sub-levels within Sable's tracking
range of the camera SHALL reach the client and be drawn, as they would for
the player standing there, also a sub-level the server had unloaded; those
only near the player then aren't sent. With the camera back, Sable SHALL
measure from the player again.

#### Scenario: A ship next to a camera far from the player
- **WHEN** a ship rests at 2000,-59,0, the player stands at 0,-60,0, and an
  agent runs `camera at 2000,-56,-8 look 2000,-59,0 away; settle`
- **THEN** `sable:info <ship>` answers on the client as on the server, and
  the camera's frame shows the ship

#### Scenario: A ship unloaded while nobody was near
- **WHEN** the camera left that ship long enough for the server to unload it
  and is placed there with `away` again
- **THEN** the server loads the ship, and the client receives and draws it

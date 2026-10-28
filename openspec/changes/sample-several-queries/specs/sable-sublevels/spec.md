## ADDED Requirements

### Requirement: Reading the server's copy of a sub-level
`sable:info <sub-level> [brief] side=server` SHALL answer as `sable:info`
does, from the integrated server's copy of the sub-level: its pose, velocity,
bounds, whether the player rides it, and its height above ground measured in
the server's level. It SHALL also find a sub-level the client has not
received, by UUID or name. Like other `side=server` reads it needs cheats on.

#### Scenario: The copy the server simulates
- **WHEN** an agent runs `sable:info ship brief side=server` while the ship
  flies
- **THEN** the reply gives `yaw`, `pitch`, `roll`, `speed`, `climb`, `y` and
  `agl` of the integrated server's copy

#### Scenario: A sub-level the client has not received
- **WHEN** the integrated server holds a ship the client has not received,
  and an agent runs `sable:info <its UUID> side=server`
- **THEN** the reply gives its pose and `agl`, while `sable:info <its UUID>`
  fails with `err no_sublevel`

#### Scenario: No cheats
- **WHEN** an agent without cheats runs `sable:info ship side=server`
- **THEN** the result is `err no_cheats`

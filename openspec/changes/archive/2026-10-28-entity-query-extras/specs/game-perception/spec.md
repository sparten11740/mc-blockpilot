## MODIFIED Requirements

### Requirement: Entity query
`entity <e<id>|me>` SHALL answer one entity's motion as the client has it: its
type, its `e<id>`, absolute position to the thousandth of a block, yaw and
pitch to the tenth of a degree, velocity in blocks per tick to the
thousandth, `airborne` when it doesn't stand on the ground, and its age in
ticks. `me` names the player. An entity the client doesn't hold SHALL fail
with `err no_entity`.

#### Scenario: A thrown item on the client
- **WHEN** an agent runs `entity e5` for an item the player has just thrown
- **THEN** the reply is like `ok item e5 pos 12.304,65.871,-3.502 yaw12.5
  pitch0 vel 0.112,0.207,-0.031 airborne age=12`

#### Scenario: The player
- **WHEN** an agent runs `entity me`
- **THEN** the reply names the player's type and `e<id>`, with its motion

#### Scenario: Gone
- **WHEN** an agent runs `entity e5` after the item was picked up
- **THEN** the result is `err no_entity e5`

### Requirement: Entity query on the server
`entity <e<id>|me> side=server` SHALL answer the same fields as the
integrated server has the entity, adding its UUID; `me` is the player's
server copy. Like other `side=server` reads it needs cheats on, and an
entity the server doesn't hold SHALL fail with `err no_entity`.

#### Scenario: The server's copy
- **WHEN** an agent runs `entity e5 side=server` in singleplayer with cheats
  on
- **THEN** the reply gives the same fields as the integrated server has the
  entity, and its `uuid`

#### Scenario: Both sides sampled
- **WHEN** an agent runs `sample entity e5 and entity e5 side=server every 1t
  x20` while the item flies
- **THEN** each of the twenty ticks gives a client row and a server row with
  the same tick, so their positions can be compared

## ADDED Requirements

### Requirement: Entity updates the client received
On the client, `entity` SHALL also give the updates the client applied to the
entity since its previous tick, counted by kind: `move`, `teleport`,
`motion` and `data`. It SHALL leave the field out when none arrived.

#### Scenario: Updates arriving in bursts
- **WHEN** an agent runs `sample entity e5 every 1t x20` while position
  updates for the entity arrive unevenly
- **THEN** rows with no update carry no `net`, and a row after a burst
  carries counts such as `net move3`

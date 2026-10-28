## ADDED Requirements

### Requirement: Landing on a moving sub-level
`sable:tp` SHALL leave the player on the spot it chose in the sub-level's own
frame, also while the sub-level moves or turns: it SHALL answer only once the
client shows the player standing on that sub-level within about half a block
of the spot, and a flying player SHALL stop flying. When the client doesn't
show that within five seconds, it SHALL fail with `err not_aboard`, saying
what the client shows instead.

#### Scenario: Onto a ship at speed
- **WHEN** an agent runs `sable:tp <plot position of a lever>` on a ship
  flying at 50 blocks per second
- **THEN** sampling `entity me` beside `sable:world` of the spot's floor
  centre over the next ticks shows the player within half a block of the
  spot, `status` shows the player aboard, and `use` at the lever's plot
  position uses it

#### Scenario: Never aboard
- **WHEN** the client doesn't show the player standing on the sub-level
  within five seconds, e.g. because the client never receives it
- **THEN** the result is `err not_aboard`, naming what the client shows

### Requirement: Boarding and leaving events
When the sub-level carrying the player changes, Blockpilot SHALL deliver
`! sable:left <sub>` for the one the player no longer stands on or rides,
then `! sable:boarded <sub>` for the one they now do, each naming the
sub-level as `status` does, with its UUID in JSON. A sub-level that unloads
or is removed under the player counts as left. Joining or leaving a world
SHALL deliver neither.

#### Scenario: Falling off a ship
- **WHEN** the player stands on a ship and walks off its deck
- **THEN** the next reply carries `! sable:left <ship>`

#### Scenario: Boarding with sable:tp
- **WHEN** the player stands on the ground and an agent runs `sable:tp
  <plot position aboard a ship>`
- **THEN** the reply carries `! sable:boarded <ship>`

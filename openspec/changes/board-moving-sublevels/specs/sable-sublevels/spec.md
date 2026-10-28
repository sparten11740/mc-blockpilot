## MODIFIED Requirements

### Requirement: Teleporting next to a block aboard
`sable:tp <plot position>` SHALL put the player on a free spot next to that
block aboard, in a world with commands allowed, and answer with where the
player now stands and `dist`, from the player's eyes to the block's centre.
Of the free spots within two blocks it SHALL take the nearest from which the
block is in sight; when none sees it, the nearest, adding `hidden`. When no
spot within two blocks has room, it SHALL fail with `err no_room`.

The player SHALL end on that spot in the sub-level's own frame, also while
the sub-level moves or turns: `sable:tp` SHALL answer only once the client
shows the player standing on that sub-level within about half a block of
the spot, and a flying player SHALL stop flying. When the client doesn't
show that within five seconds, it SHALL fail with `err not_aboard`, saying
what the client shows instead.

#### Scenario: Onto the bridge
- **WHEN** an agent runs `sable:tp <plot position of the captain's seat>`
- **THEN** the player stands next to the seat aboard the ship, and
  `status` shows them riding it

#### Scenario: Inside the hull, not on it
- **WHEN** an agent runs `sable:tp <plot position of a lever>` inside a cabin
  whose window is next to the lever, with room both inside and on top of the
  window
- **THEN** the player stands inside the cabin, and the reply has `dist` but
  no `hidden`

#### Scenario: Block sealed in
- **WHEN** the block is walled in on every side, and the only free spot within
  two blocks is outside the wall
- **THEN** the player stands on that spot, and the reply carries `hidden`

#### Scenario: Onto a ship at speed
- **WHEN** an agent runs `sable:tp <plot position of a lever>` on a ship
  flying at 50 blocks per second
- **THEN** `sable:plot here ~0,0,0` right after the reply is within half a
  block of the reply's `plot`, `status` shows the player aboard, and `use`
  at the lever's plot position uses it

#### Scenario: Never aboard
- **WHEN** the client doesn't show the player standing on the sub-level
  within five seconds, e.g. because the client never receives it
- **THEN** the result is `err not_aboard`, naming what the client shows

## ADDED Requirements

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

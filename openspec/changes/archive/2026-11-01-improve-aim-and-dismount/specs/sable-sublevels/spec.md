## MODIFIED Requirements

### Requirement: Aiming follows a sub-level
`face` and `use` at a plot position SHALL aim at that block where its sub-level
is on each tick, so a moving or turning sub-level doesn't throw the aim off,
whether the player stands beside it or rides it. The `face` and `use` help
SHALL say so.

#### Scenario: Use while the ship moves
- **WHEN** an agent runs `use <plot position of a lever>` on a ship under way,
  with a clear line of sight
- **THEN** the lever is used, although its world position changed during the
  aim

#### Scenario: Use while riding the ship
- **WHEN** an agent seated aboard a ship flying at speed runs `use <plot
  position of a lever aboard>`
- **THEN** the lever is used on the first try

#### Scenario: Help names plot positions
- **WHEN** an agent runs `help face` or `help use` with Sable installed
- **THEN** the help says that a plot position follows its sub-level on each
  tick

### Requirement: Cameras follow a sub-level
`camera at` and its `look` target SHALL accept plot positions and, for a plot
position, follow its sub-level each rendered frame, so a camera placed on or
aimed at a ship rides along with it. The `camera` help SHALL say so. With
`clear`, the look point SHALL first be put into the camera point's frame, the
plot of the camera point's sub-level or else the world, so that `clear` steps
along the line the camera actually looks along.

#### Scenario: Chase camera
- **WHEN** an agent runs `camera at <plot position behind the stern> look
  <plot position of the helm>` while the ship moves
- **THEN** screenshots taken along the way keep the ship in the same place in
  the frame

#### Scenario: Clear inside a hull toward the player
- **WHEN** an agent runs `camera at <plot position inside a hull block> clear`
  without `look`, with the player outside the ship
- **THEN** the camera moves out of the hull block toward the player and the
  reply carries `cleared=` of at most a few blocks

#### Scenario: Help names plot positions
- **WHEN** an agent runs `help camera` with Sable installed
- **THEN** the help says that a plot position follows its sub-level on each
  frame

## ADDED Requirements

### Requirement: Camera moves follow a sub-level
`camera move` SHALL accept plot positions for its point, its via points and
its `look` target and, for a plot position, follow the sub-level in each
rendered frame, so a path bends with a moving ship and a move onto one ends
riding with it. A move that starts from a camera riding a sub-level SHALL
start from where that camera is drawn in each frame. The `camera` help SHALL
say so.

#### Scenario: Onto a moving ship
- **WHEN** an agent runs `camera move <plot position behind the stern> look
  <plot position of the helm> over 3s` while the ship moves
- **THEN** the camera closes in on the ship, and screenshots taken after the
  reply keep the ship in the same place in the frame

#### Scenario: Over the deck on the way
- **WHEN** an agent runs `camera move <a world position ahead of the ship>
  via <plot position above the deck> over 4s` while the ship moves
- **THEN** the camera passes above the deck where the ship is when it gets
  there, and ends at the world position

#### Scenario: Leaving a ship
- **WHEN** a camera rides at a plot position and an agent runs `camera move
  <a world position beside the ship's course> over 2s`
- **THEN** the camera sets off from where the ship carries it and ends at
  the world position, which stays put as the ship sails on

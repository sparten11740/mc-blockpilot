## ADDED Requirements

### Requirement: A placed camera's field of view
`fov=<degrees>` SHALL set a placed camera's vertical field of view, from 1
to 170, in place of the player's and its effects, and `status` SHALL name a
placed camera's own field of view as `cam_fov`.

#### Scenario: A narrow view
- **WHEN** an agent runs `camera at ~0,2,-20 look ~0,1,0 fov=10; pixels
  mid=0.47,0.47,0.53,0.53` with a red block at `~0,1,0`
- **THEN** `mid`'s mean colour is red, where with the player's field of
  view of 70 the block is too small to fill it

### Requirement: A placed camera's roll
`roll=<degrees>` SHALL tilt a placed camera about the way it looks, from
-180 to 180, a positive roll leaning it to its right, and `status` SHALL
give the roll in `cam:`.

#### Scenario: A tilted horizon
- **WHEN** an agent runs `camera at ~0,2,0 look ~0,2,10 roll=90; pixels
  left=0,0.4,0.2,0.6 right=0.8,0.4,1,0.6; status`
- **THEN** `left`'s mean colour is the sky's blue and `right`'s the
  ground's, and `status` gives a roll of 90 in `cam:`

### Requirement: Setting and gliding the lens
`camera at` and `camera follow` SHALL set `fov=` and `roll=` at once, and
`camera move` and `camera orbit` SHALL glide from the values in use to the
given ones along the motion, shaped by its `ease=`. A value left out SHALL
stay as it is until `camera reset`, which returns to the player's view.

#### Scenario: A zoom along a move
- **WHEN** an agent runs `sample status every 10t while camera move ~0,2,-8
  look ~0,1,0 over 40t fov=30` from `camera at ~0,2,-6 look ~0,1,0 fov=70`
- **THEN** the row taken 20 ticks in names `cam_fov` about 50, and the last
  row 30

#### Scenario: Kept through the next placement
- **WHEN** an agent runs `camera at ~0,2,-6 fov=30; camera at ~4,2,-6;
  status`
- **THEN** `status` names `cam_fov=30`

### Requirement: Lens values out of range
A `fov=` or `roll=` out of its range, or either option on `camera reset`,
`off` or `wait`, SHALL fail with `err syntax` naming the range or the forms
that take it, before anything changes.

#### Scenario: Out of range
- **WHEN** an agent runs `camera at ~0,2,0 fov=200`
- **THEN** the result is `err syntax` naming 1 to 170, and no camera is
  placed

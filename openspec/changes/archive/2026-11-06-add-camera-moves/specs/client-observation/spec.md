## ADDED Requirements

### Requirement: Camera moves
`camera move <pos> [via <pos> …] [look <pos|e<id>|ahead>] over <duration>`
SHALL glide the camera from where it is drawn now, a placed camera or else
the player's eyes, to the point over the duration, counted in client ticks,
placing it anew in every rendered frame. The move SHALL end that many ticks
after it starts and leave the camera placed at the point, as `camera at`
does, and its reply SHALL be that of `camera at` for the point, once a frame
from there has rendered.

#### Scenario: A dolly in one step
- **WHEN** an agent runs `camera at ~0,2,-6 look ~0,1,0` and then
  `camera move ~6,2,0 look ~0,1,0 over 40t`
- **THEN** the second reply comes 40 client ticks or a few frames more after
  the step starts, with `camera` at the absolute position of `~6,2,0`, and a
  `screenshot` after it carries that position

#### Scenario: Smooth in a recording
- **WHEN** an agent records at `fps=60` while `camera move ~10,2,0 look
  ~0,1,0 over 2s` runs, and the game renders at least 60 frames a second
- **THEN** the camera is in a new place in nearly every frame of the video,
  where one `camera at` per tick repeats each place for about three frames

#### Scenario: From the player's view
- **WHEN** an agent runs `camera move ~0,8,0 over 20t` with no camera placed
- **THEN** the view starts at the player's eyes, rises 8 blocks keeping the
  player's view direction, and the GUI and hand are hidden as with
  `camera at`

### Requirement: Curved camera moves
With `via <pos>`, given up to 32 times, a camera move SHALL pass through each
via point in order on one smooth curve through its start, the via points and
its point, with no corner at any of them, and SHALL go at a constant speed
along the whole curve, which `ease=` then shapes. Without `via`, the camera
SHALL go in a straight line. A 33rd `via` SHALL fail with `err syntax` naming
the limit.

#### Scenario: Round a corner
- **WHEN** an agent runs `sample status every 2t while camera move ~10,2,10
  via ~10,2,0 over 40t` from a camera placed at `~0,2,0`
- **THEN** one row's `camera` position is within half a block of
  `~10,2,0`, and from row to row the direction of travel turns by a third of
  a quarter circle at most, spreading the turn over several rows, never by a
  quarter circle at once

#### Scenario: Constant speed
- **WHEN** an agent runs `sample status every 4t while camera move ~12,2,0
  via ~2,2,0 over 40t` from a camera placed at `~0,2,0`, whose first stretch
  is a sixth of its length
- **THEN** successive rows' `camera` positions are about the same distance
  apart all along the path

#### Scenario: Too many via points
- **WHEN** an agent runs `camera move` with 33 `via` points
- **THEN** the result is `err syntax` naming the limit of 32

### Requirement: Where a moving camera looks
Without `look`, a camera move SHALL keep looking at what the camera looked
at, or along the player's view direction when it starts from the player's
view. With a point or an entity, the view SHALL turn from its old direction
to the new target's over the move, with the same easing, without turning the
long way round. With `look ahead`, it SHALL turn onto the direction of
travel within the first fifth of the move, then look along the path, and
keep the last direction once the move ends.

#### Scenario: Keeps its target
- **WHEN** an agent runs `camera at ~0,2,-6 look e<id of a cow>` and then
  `camera move ~6,2,0 over 40t`
- **THEN** the cow stays in the middle of the frame throughout

#### Scenario: Pans to a new target
- **WHEN** an agent runs `camera at ~0,2,0 look ~10,2,0` and then
  `camera move ~0,2,0 look ~0,2,10 over 20t`
- **THEN** the view turns a quarter circle, and halfway through it faces
  between the two targets

#### Scenario: Looking ahead round a corner
- **WHEN** an agent runs `sample status every 2t while camera move ~10,2,10
  via ~10,2,0 look ahead over 40t` from a camera placed at `~0,2,0`
- **THEN** from a fifth of the way on, each row's `cam_yaw` points along the
  way the `camera` position moves through it, from the row before to the row
  after, within a few degrees

### Requirement: Camera move easing
`ease=` SHALL shape a camera move's speed along its path: `linear`, the
default, keeps it constant; `in` starts slowly; `out` ends slowly; `inout`
starts and ends slowly. Every easing SHALL reach the point at the end of the
duration.

#### Scenario: Halfway
- **WHEN** an agent runs `sample status every 10t while camera move ~10,2,0
  over 40t` from a camera placed at `~0,2,0`, once with each easing
- **THEN** the row taken 20 ticks in has the camera about 5 blocks along
  with `linear` and `inout`, under 5 with `in` and over 5 with `out`

#### Scenario: Unknown easing
- **WHEN** an agent runs `camera move ~10,2,0 over 40t ease=fast`
- **THEN** the result is `err syntax` naming `linear`, `in`, `out` and
  `inout`

### Requirement: A camera move ended early
`stop` and real input during agent control SHALL end a camera move before
its time. The camera SHALL then stay placed where the last frame drew it,
looking the same way, until the next `camera` command, and the move SHALL
answer `err cancelled`, or `err interrupted` for real input.

#### Scenario: Stopped halfway
- **WHEN** session B runs `stop` 20 ticks into session A's `camera move
  ~10,2,0 over 40t` from a camera placed at `~0,2,0`
- **THEN** A's result is `err cancelled`, and `status` and a `screenshot`
  name the camera about 5 blocks along, where it stays

### Requirement: Placed camera in status
While a camera is placed, `status` SHALL name the position the last
rendered frame was drawn from as `camera <x,y,z>`, in absolute
coordinates, as a `screenshot` of that frame does. Without a placed camera
it SHALL leave `camera` out.

#### Scenario: Camera at a point
- **WHEN** an agent runs `camera at 40,90,-20; status`
- **THEN** the `status` line carries `camera 40,90,-20`

#### Scenario: Player's view
- **WHEN** an agent runs `camera reset; status`
- **THEN** the `status` line carries no `camera` position

## MODIFIED Requirements

### Requirement: Camera placement
`camera at <pos> [look <pos|e<id>>]` SHALL render the view from the given world
point, facing the given target, with the player model visible, until `camera
reset` (or `camera off`). The reply gives the camera's absolute position and
look point. While placed, the player still moves and acts as usual, and the GUI
and hand are hidden as with F1, until the reset restores them. Leaving the
world, by any means, and turning agent control off with the agent-control key
reset the camera.

#### Scenario: Outside view of the airliner
- **WHEN** an agent runs `camera at 40,90,-20 look e812; screenshot`
- **THEN** the screenshot shows the scene from that point toward the entity, and
  `camera reset` returns to first person

#### Scenario: Camera off
- **WHEN** an agent runs `camera off` with the camera placed
- **THEN** the view returns to first person, as with `camera reset`

#### Scenario: Following a walking entity
- **WHEN** an agent records at `fps=60` with `camera at ~0,2,-6 look e<id of a
  walking cow>` placed
- **THEN** the view turns with the cow where each frame draws it, so the cow
  holds its place in the frame instead of jerking once a tick

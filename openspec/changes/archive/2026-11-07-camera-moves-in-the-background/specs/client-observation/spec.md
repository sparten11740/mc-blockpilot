## ADDED Requirements

### Requirement: Camera moves in the background
`camera move … over <duration> background` SHALL answer at once with `ok
moving over=<n>t` and glide the camera on its own over the duration, as the
move would in the foreground, while the script and the scripts after it go
on. A `camera at`, `camera move` or `camera reset` SHALL replace it, and
`stop` and real input SHALL end it where the last frame drew it.

#### Scenario: Server steps while the camera moves
- **WHEN** an agent runs `camera at ~0,2,0; camera move ~10,2,0 over 40t
  background; server settle=1t time query gametime; wait 20t; status`
- **THEN** the move answers before the server step runs, and `status`
  names the camera about 5 blocks along

#### Scenario: Stopped in the background
- **WHEN** session B runs `stop` 20 ticks into a move session A started in
  the background
- **THEN** `status` names the camera part of the way along, short of the
  end, where the last frame drew it, and it stays there

### Requirement: Waiting for a camera move
`camera wait` SHALL answer once the camera move running in the background
has ended, as `camera at` answers for its end point, and at once with the
camera's position when no move runs.

#### Scenario: Wait for the end
- **WHEN** an agent runs `camera move ~10,2,0 over 40t background; camera
  wait` from a camera placed at `~0,2,0`
- **THEN** `camera wait` answers about 40 ticks later with `camera` at the
  absolute position of `~10,2,0`

#### Scenario: Nothing moving
- **WHEN** an agent runs `camera at ~0,2,0; camera wait`
- **THEN** `camera wait` answers with `camera` at the absolute position of
  `~0,2,0`

## MODIFIED Requirements

### Requirement: Curved camera moves
With `via <pos>`, given up to 32 times, a camera move SHALL pass through each
via point in order on one smooth curve through its start, the via points and
its point, with no corner at any of them, and SHALL go at a constant speed
along the whole curve, which `ease=` then shapes. Without `via`, the camera
SHALL go in a straight line. A 33rd `via` SHALL fail with `err syntax` naming
the limit.

#### Scenario: Round a corner
- **WHEN** an agent runs `sample status every 1t while camera move ~10,2,10
  via ~10,2,0 over 40t` from a camera placed at `~0,2,0`
- **THEN** one row's `camera` position is within half a block of
  `~10,2,0`, and between rows two ticks apart the direction of travel turns
  by less than half a quarter circle, spreading the turn over several rows,
  never by a quarter circle at once

#### Scenario: Constant speed
- **WHEN** an agent runs `sample status every 4t while camera move ~12,2,0
  via ~2,2,0 over 40t` from a camera placed at `~0,2,0`, whose first stretch
  is a sixth of its length
- **THEN** successive rows' `camera` positions are about the same distance
  apart all along the path, a row read from a frame the game drew late
  counting together with the row after it, and left out when it is the last

#### Scenario: Too many via points
- **WHEN** an agent runs `camera move` with 33 `via` points
- **THEN** the result is `err syntax` naming the limit of 32

### Requirement: Camera move easing
`ease=` SHALL shape a camera move's speed along its path: `linear`, the
default, keeps it constant; `in` starts slowly; `out` ends slowly; `inout`
starts and ends slowly. Every easing SHALL reach the point at the end of the
duration.

#### Scenario: Halfway
- **WHEN** an agent runs `sample status every 1t while camera move ~10,2,0
  over 40t` from a camera placed at `~0,2,0`, once with each easing
- **THEN** the row taken 20 ticks in has the camera about 5 blocks along
  with `linear` and `inout`, under 5 with `in` and over 5 with `out`; when
  that row read a frame the game drew late, repeating the row before, the
  next row read from a new frame counts instead

#### Scenario: Unknown easing
- **WHEN** an agent runs `camera move ~10,2,0 over 40t ease=fast`
- **THEN** the result is `err syntax` naming `linear`, `in`, `out` and
  `inout`

### Requirement: An away camera answers once its chunk has come
`camera at … away` SHALL answer once the client has the camera's chunk, or
after 10 seconds, and a camera move from a camera placed with `away` SHALL
keep it away, so the server's world follows the camera along the move.

#### Scenario: A move from the set
- **WHEN** an agent runs `camera move 1400,-58,0 look 1400,-60,5 over 40t;
  settle; block 1400,-61,0` with that camera placed
- **THEN** `block` reads `grass_block`: the server's world followed the
  camera along the move, whether or not its chunks had arrived when the move
  answered

### Requirement: The player waits while the camera is away
While a camera is placed with `away`, the player SHALL stand where it was,
neither moving nor falling, until the camera resets, and while that camera
is farther from the player than the view distance, the server SHALL send
nothing around the player; after the reset the server SHALL send the chunks
around the player again.

#### Scenario: Standing still
- **WHEN** an agent runs `status; wait 1s; status` with a camera placed 1000
  blocks away with `away`
- **THEN** both `status` replies give the same position

#### Scenario: Back to the player
- **WHEN** an agent runs `camera reset; settle; block ~0,-1,0`
- **THEN** `settle` answers `settled=`, `block` reads the block under the
  player, and the player moves again

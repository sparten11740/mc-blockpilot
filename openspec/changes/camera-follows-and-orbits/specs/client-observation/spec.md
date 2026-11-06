## ADDED Requirements

### Requirement: Following an entity with the camera
`camera follow <entity> [at <~dx,dy,dz>]` SHALL place the camera at an
offset from the entity in every rendered frame, read where that frame draws
the entity, looking at the point `camera at … look` takes for it, until the
next `camera` command: the offset given with `at`, or else the one the
camera has from the entity when the command runs. The reply SHALL be that
of `camera at` for where the camera sits. `at` with a position that isn't
relative SHALL fail with `err syntax`.

#### Scenario: The framing goes along
- **WHEN** an agent runs `camera at ~0,2,-6 look @walker; camera follow
  @walker`, then `server execute as @e[tag=walker] at @s run tp @s ~8 ~ ~;
  wait 5t; status`, with an armor stand tagged `walker` standing at `~3,0,3`
- **THEN** `status` names the camera 8 blocks east of `~0,2,-6`

#### Scenario: An absolute offset
- **WHEN** an agent runs `camera follow @walker at 0,2,-6`
- **THEN** the result is `err syntax` saying the offset is relative

### Requirement: A following camera that turns
With `turning`, `camera follow` SHALL keep the offset in the entity's frame,
`x` to its left, `y` up and `z` the way it faces, and turn it as the entity
turns, catching up with a quick turn over a second or two.

#### Scenario: Over the shoulder
- **WHEN** an agent runs `camera follow @walker at ~0,2,-6 turning` with the
  walker facing south, then turns it to face west with `server execute as
  @e[tag=walker] at @s run tp @s ~ ~ ~ 90 0`, waits 3 seconds and runs
  `status`
- **THEN** `status` names the camera about 6 blocks east of the walker and
  2 above its feet, behind it as it now faces

### Requirement: What a follow outlasts
`stop` and real input SHALL leave a follow in place, as they leave `camera
at`. When the entity is removed, the camera SHALL stay where the last frame
drew it, looking the same way.

#### Scenario: Stop leaves a follow
- **WHEN** session B runs `stop` while session A's `camera follow @walker`
  is placed, and then `server execute as @e[tag=walker] at @s run tp @s ~8 ~
  ~; wait 5t; status`
- **THEN** the camera moves 8 blocks east with it

#### Scenario: The entity goes
- **WHEN** an agent runs `server kill @e[tag=walker]; wait 5t; status; wait
  1s; status` while `camera follow @walker` is placed
- **THEN** both `status` replies name the camera at the same position

### Requirement: Orbiting with the camera
`camera orbit <pos|entity> [by <degrees>] over <duration>` SHALL circle the
camera round the centre from where the camera is drawn now, keeping its
distance across and its height from the centre and looking at the centre,
by the angle or a full turn without `by`: a positive angle to the camera's
right as it faces the centre, a negative one to its left. At its end the
camera SHALL stay where the orbit left it.

#### Scenario: A quarter turn
- **WHEN** an agent runs `sample status every 5t while camera orbit ~0,1,0
  by 90 over 40t` from a camera placed at `~0,3,-6` looking at `~0,1,0`
- **THEN** every row's `camera` is about 6 blocks across from `~0,1,0` and 2
  above it, and the orbit answers with `camera` at the absolute position of
  `~-6,3,0`

### Requirement: An orbit is timed as a move
An orbit SHALL take its duration in client ticks, shaped by `ease=`, and
answer as `camera at` at its end, or at once with `ok moving over=<n>t`
with `background`; `camera wait`, `stop`, real input and a newer `camera`
command SHALL treat it as they treat a camera move. `by 0` SHALL hold the
camera where it is for the duration.

#### Scenario: Waiting for an orbit in the background
- **WHEN** an agent runs `camera orbit ~0,1,0 by -90 over 40t background;
  camera wait` from a camera placed at `~0,3,-6`
- **THEN** `camera orbit` answers at once, and `camera wait` about 40 ticks
  later with `camera` at the absolute position of `~6,3,0`

### Requirement: An orbit round an entity
An entity centre SHALL be read where each frame draws it, at the point
`camera at … look` takes for it, so the circle goes along as the entity
moves. When the entity is removed, the camera SHALL stay where the last
frame drew it, looking the same way, and the orbit SHALL still answer at its
time.

#### Scenario: Round a moving entity
- **WHEN** an agent runs `camera orbit @walker over 40t background; server
  execute as @e[tag=walker] at @s run tp @s ~10 ~ ~; wait 20t; status` from
  a camera placed 6 blocks from the walker
- **THEN** `status` names the camera about 6 blocks across from where the
  walker now stands

#### Scenario: The centre goes
- **WHEN** an agent runs `camera orbit @walker over 40t background; server
  kill @e[tag=walker]; wait 5t; status; wait 10t; status; camera wait`
- **THEN** both `status` replies name the camera at the same position, and
  `camera wait` answers about 40 ticks after the orbit began

### Requirement: A following or orbiting camera stays away
`camera follow` and `camera orbit` from a camera placed with `away` SHALL
keep it away, as a camera move does, so the server's world follows the
camera.

#### Scenario: An orbit far from the player
- **WHEN** an agent runs `camera at 1000,-58,0 look 1000,-60,5 away; camera
  orbit 1000,-60,5 by 180 over 40t; settle; block 1000,-61,10`
- **THEN** `block` reads `grass_block`, and the player stands where it was

# Spec Delta

## Purpose

Lets an agent observe what a human would notice while play-testing: sounds,
on-screen messages, particles, short-lived visuals and the camera as rendered.
These signals are otherwise invisible in text.

## ADDED Requirements

### Requirement: Sound log
The client SHALL record every sound it starts playing: sound event id, category,
position, volume, pitch and the tick. It keeps the last 2,000 sounds. `sounds
[since=<tick>] [match=<pattern>] [limit=<n>]` SHALL return matching sounds
oldest first, plus the current tick as `tick` for the next `since`.

#### Scenario: Gear sound verified
- **WHEN** a mod plays `aerolarge:gear_extend` at the landing gear and an agent
  then runs `sounds since=<tick> match=aerolarge:*`
- **THEN** a row lists the sound with its category, position relative to the
  player, volume and pitch

#### Scenario: Waiting for a sound
- **WHEN** an agent runs `wait sounds match=*thunder* since=<tick> until count>0
  timeout=10s`
- **THEN** the wait returns as soon as a thunder sound has played, or fails with
  `err timeout`

### Requirement: Message log
The client SHALL record chat, action-bar, title and subtitle messages with their
kind and tick. It keeps the last 500. `messages [since=<tick>]
[kind=chat|actionbar|title|subtitle]` SHALL return them oldest first.
Action-bar, title and subtitle messages are also delivered as events.

#### Scenario: Action-bar feedback
- **WHEN** a mod shows "Gear refused: airspeed too high" in the action bar
- **THEN** the next reply carries `! actionbar Gear refused: airspeed too high`,
  and `messages kind=actionbar` lists it

### Requirement: Particle counts
The client SHALL count the particles it spawns per particle type and tick, for
the last 1,200 ticks. `particles [since=<tick>]` SHALL return the count per type
over the window, most frequent first, with the total.

#### Scenario: Laser impact particles
- **WHEN** a laser shot spawns 40 crit particles and an agent runs `particles
  since=<tick before the shot>`
- **THEN** a row reports `crit x40`

### Requirement: Screenshot bursts
`screenshot burst=<n> every=<Nt> [width]` SHALL capture n frames (at most 60),
one every N ticks, starting with the next frame, and return the saved files in
order with the tick of each.

#### Scenario: Gear animation
- **WHEN** an agent runs `use; screenshot burst=8 every=20t` on the gear lever
- **THEN** eight PNG files covering 8 seconds of the animation are listed, each
  with its tick

### Requirement: Camera placement
`camera at <pos> [look <pos|e<id>>]` SHALL render the view from the given world
point, facing the given target, with the player model visible, until `camera
reset`. While placed, the player still moves and acts as usual. Leaving the
world resets the camera.

#### Scenario: Outside view of the airliner
- **WHEN** an agent runs `camera at 40,90,-20 look e812; screenshot`
- **THEN** the screenshot shows the scene from that point toward the entity, and
  `camera reset` returns to first person

### Requirement: Rendered camera angles
When the rendered camera's yaw, pitch or roll differs from the player's own
rotation by more than 0.5 degrees (camera shake, roll, or a placed camera),
`status` SHALL report the rendered angles as `cam_yaw`, `cam_pitch` and
`cam_roll`. A loader that can't observe roll reports yaw and pitch only.

#### Scenario: Air pocket camera dip
- **WHEN** a mod tilts the camera 6 degrees during an air pocket
- **THEN** `status` includes `cam_roll` near 6 while the player's own pitch is
  unchanged

### Requirement: Sampling client queries
`sample <query> every <Nt> x<n>` SHALL also run client-side read-only queries,
on the client clock (the default), returning one row per run with the tick and
the query's fields, at most 1,000 runs. Server-clock sampling is specified by
tick-control.

#### Scenario: Per-tick player state
- **WHEN** an agent runs `sample status every 1t x40` during an air pocket
- **THEN** 40 rows are returned, one per tick, each with position, velocity and
  on-ground state

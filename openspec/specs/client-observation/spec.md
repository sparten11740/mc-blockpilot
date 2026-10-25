# client-observation Specification

## Purpose

Lets an agent observe what a human would notice while play-testing: sounds,
on-screen messages, particles, short-lived visuals, the brightness of what was
rendered and the camera as rendered. These signals are otherwise invisible in
text. It also sets the client options a test needs, and puts them back.

## Requirements

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
Action-bar, title and subtitle messages are also delivered as events. Mods
refresh the action bar every tick, so the same action-bar text within 2 seconds
of its last refresh is neither logged nor delivered again.

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
`screenshot burst=<n> every=<Nt> [width] [hud=off]` SHALL capture n frames (at
most 60), one every N ticks, starting with the next frame, and return the saved
files in order with the tick of each. `hud=off` hides the GUI for the captured
frames.

#### Scenario: Gear animation
- **WHEN** an agent runs `use; screenshot burst=8 every=20t` on the gear lever
- **THEN** eight PNG files covering 8 seconds of the animation are listed, each
  with its tick

### Requirement: Screenshot crops and contact sheets
`screenshot` SHALL accept `crop=<x0>,<y0>,<x1>,<y1>`, fractions of the frame
measured from the top left, and capture only that part of each frame, before
scaling to the width. With `burst=<n>` of at least 2, `sheet` SHALL save the
frames as one PNG instead: a grid with `ceil(sqrt(n))` columns, each tile
labelled with its frame number and tick (`#3 t1234`), and tiles scaled so the
sheet is at most 1,536 pixels wide. The reply gives the sheet's file and one
row per frame with its tick.

#### Scenario: Gear retraction in one image
- **WHEN** an agent runs `screenshot burst=8 every=20t sheet
  crop=0.3,0.3,0.7,0.7`
- **THEN** one PNG holds the eight cropped frames in a three-column grid, each
  labelled with its tick

#### Scenario: Sheet without a burst
- **WHEN** an agent runs `screenshot sheet`
- **THEN** the result is `err syntax`, because a sheet needs `burst=<n>` of at
  least 2

### Requirement: Pixel probe
`pixels [<region>…]` SHALL measure regions of the last rendered frame, GUI
included unless hidden (`option hud off`), and report for each the mean red,
green and blue (0–255) and the mean, maximum and 90th-percentile luma, as the
fields `<name>.r`, `.g`, `.b`, `.luma`, `.max` and `.p90`, so `wait`, `assert`
and `if` can test them. `pixels` changes nothing.

#### Scenario: Flame brighter than the sky
- **WHEN** an agent runs `assert pixels flame=@3,70,12 sky=0.4,0.05,0.6,0.15
  flame.max>$sky.luma`
- **THEN** the assert passes when the brightest flame pixel outshines the sky's
  mean, and the reply carries both values

### Requirement: Pixel probe regions
A region SHALL be `[<name>=]<x0>,<y0>,<x1>,<y1>` in frame fractions, or
`[<name>=]@<pos>`: the square around a world position, as projected through
that frame's camera, reaching `r=<blocks>` (default 0.5) to each side. Unnamed
regions are called `r0`, `r1`, and so on. With no region, `all` and `centre`
(the middle tenth of each axis) SHALL be measured. A world position behind the
camera or outside the frame SHALL fail with `err offscreen <name>`.

#### Scenario: Default regions
- **WHEN** an agent runs `pixels` after `camera at ~5,2,0 look ~0,1,0`
- **THEN** the reply measures `all` and `centre`, and `centre` is the area
  around the look point

#### Scenario: Point behind the camera
- **WHEN** the position given with `@` lies behind the camera
- **THEN** the result is `err offscreen` with the region's name

### Requirement: Camera placement
`camera at <pos> [look <pos|e<id>>]` SHALL render the view from the given world
point, facing the given target, with the player model visible, until `camera
reset`. The reply gives the camera's absolute position and look point. While
placed, the player still moves and acts as usual, and the GUI and hand are
hidden as with F1, until the reset restores them. Leaving the world, by any
means, and turning agent control off with the agent-control key reset the
camera.

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

### Requirement: Client options
`option` SHALL list the supported client options with their values, showing the
original value of each one an agent changed. `option <name>` SHALL report the
value and what the option accepts, `option <name> <value>` SHALL set it, and
`option reset` SHALL restore every option agents changed. Blockpilot SHALL NOT
write `options.txt`.

#### Scenario: Flames gone on Fast
- **WHEN** an agent runs `option graphicsMode fast; screenshot; option reset`
- **THEN** the screenshot shows the rebuilt terrain in Fast graphics, and the
  reset brings back the player's previous mode

### Requirement: Supported options
The option names SHALL be those of `options.txt`: `graphicsMode`, `particles`,
`renderDistance`, `simulationDistance`, `renderClouds`, `ao`, `entityShadows`,
`biomeBlendRadius`, `mipmapLevels`, `entityDistanceScaling`, `maxFps`,
`enableVsync`, `prioritizeChunkUpdates`, `fov`, `gamma`, `guiScale`, `bobView`,
`fovEffectScale`, `screenEffectScale`, `darknessEffectScale`,
`damageTiltStrength`, `hideLightningFlashes`, `glintStrength` and
`glintSpeed`, plus `hud` (`on|off`, the F1 switch).

#### Scenario: Unknown option
- **WHEN** an agent runs `option shaderPack on`
- **THEN** the result is `err syntax` pointing to `option` for the list

### Requirement: Option values
Choices SHALL be written by name in lower case (`fast`, `fancy`, `fabulous`),
switches as `on|off`, and numbers within the option's range. Anything else
SHALL fail with `err syntax` naming what the option accepts.

#### Scenario: Value out of range
- **WHEN** an agent runs `option renderDistance 99`
- **THEN** the result is `err syntax` naming the accepted range

### Requirement: Settling after an option change
After a change, `option` SHALL answer once the chunk compile queue has stayed
empty for 3 ticks, with `settled=<seconds>`, or at `timeout=` (default 20s)
with `unsettled pending=<n>`. Either way the option keeps its new value.

#### Scenario: Render distance raised
- **WHEN** an agent runs `option renderDistance 16`
- **THEN** the reply comes after the newly visible sections have compiled, with
  `settled=` and the time it took

### Requirement: Agent changes end with agent control
Options agents changed SHALL be restored on `leave`, when the world is left in
any way Blockpilot did not start (the pause menu, a kick, a lost connection),
when the agent-control key turns agent control off, and when the client shuts
down. Blockpilot's own world switches (`join`, snapshot restore and test-world
creation) keep them. Closing a session keeps them too, because one-shot
clients open a connection per script.

#### Scenario: Player quits mid-test
- **WHEN** an agent set `graphicsMode fast` and the player then quits through
  the pause menu
- **THEN** the player's previous graphics mode is back

#### Scenario: Snapshot restore keeps the settings
- **WHEN** an agent set `particles minimal` and then restores a snapshot
- **THEN** particles are still `minimal` in the rejoined world

### Requirement: Sampling client queries
`sample <query> every <Nt> x<n>` SHALL also run client-side read-only queries,
on the client clock (the default), returning one row per run with the tick and
the query's fields, at most 1,000 runs. Server-clock sampling is specified by
tick-control. Every tick in this capability (sound, message and particle logs,
their `since=`, and sample rows) is the tick of tick-control's one clock, so a
tick from any reply can be passed to any `since=`.

#### Scenario: Per-tick player state
- **WHEN** an agent runs `sample status every 1t x40` during an air pocket
- **THEN** 40 rows are returned, one per tick, each with position, velocity and
  on-ground state

### Requirement: Camera placement reports its block
The reply to `camera at` SHALL name the block the camera sits in. When that
block is solid, so the view would show its inside, the reply SHALL say so with
`inside=<block>`, and the camera is placed all the same.

#### Scenario: Camera in a panel
- **WHEN** an agent runs `camera at <position inside an overhead panel>`
- **THEN** the reply carries `inside=` with the panel's block id, warning that
  the view starts inside a solid block

#### Scenario: Camera in the air
- **WHEN** an agent runs `camera at ~0,10,0`
- **THEN** the reply names `air` as the camera's block and carries no `inside=`

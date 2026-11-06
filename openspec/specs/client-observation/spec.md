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
oldest first, plus the current tick as `tick` for the next `since`. A row's
position SHALL be relative to where the player stands when the query runs, and
its JSON SHALL add `at_abs`, the absolute position the sound started at.

#### Scenario: Gear sound verified
- **WHEN** a mod plays `aerolarge:gear_extend` at the landing gear and an agent
  then runs `sounds since=<tick> match=aerolarge:*`
- **THEN** a row lists the sound with its category, position relative to the
  player, volume and pitch

#### Scenario: Player moved since the sound
- **WHEN** a sound starts at the player, who then rises 100 blocks before an
  agent runs `sounds`
- **THEN** its row reads about `~0,-100,0`, and `at_abs` is the point it started
  at

#### Scenario: Waiting for a sound
- **WHEN** an agent runs `wait sounds match=*thunder* since=<tick> until count>0
  timeout=10s`
- **THEN** the wait returns as soon as a thunder sound has played, or fails with
  `err timeout`

### Requirement: Message log
The client SHALL record chat, action-bar, title and subtitle messages with their
kind and tick. It keeps the last 500. `messages [since=<tick>]
[kind=chat|actionbar|title|subtitle] [match=<pattern>]` SHALL return them
oldest first; `match=` keeps those whose whole text fits the pattern, ignoring
case, with `*` for any run of characters.

#### Scenario: Waiting for a chat line
- **WHEN** an agent runs `wait messages since=<tick> match=*engaged* until
  count>0 timeout=30s` and a mod later says "Autopilot engaged" in chat
- **THEN** the wait returns `ok` with `count=1` as soon as that message is
  logged, other chat lines in between don't end it, and `messages
  since=<tick>` then reads the message

### Requirement: Message events
Action-bar, title and subtitle messages SHALL also be delivered as events. Mods
refresh the action bar every tick, so the same action-bar text within 2 seconds
of its last refresh is neither logged nor delivered again.

#### Scenario: Action-bar feedback
- **WHEN** a mod shows "Gear refused: airspeed too high" in the action bar
- **THEN** the next reply carries `! actionbar Gear refused: airspeed too high`,
  and `messages kind=actionbar` lists it

### Requirement: Particle counts
The client SHALL count the particles it spawns per particle type and tick, for
the last 1,200 ticks that had any. `particles [since=<tick>]` SHALL return the
count per type over the window, most frequent first, with the total.

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
`option reset` SHALL restore every option agents changed, including options the
game changed along with the one an agent set. Blockpilot SHALL NOT write
`options.txt`.

#### Scenario: Flames gone on Fast
- **WHEN** an agent runs `option graphicsMode fast; screenshot; option reset`
- **THEN** the screenshot shows the rebuilt terrain in Fast graphics, and the
  reset brings back the player's previous mode

#### Scenario: A preset and what it set
- **WHEN** on 26.1 an agent runs `option graphicsPreset fast`, which also sets
  the render distance, the leaves and a dozen other options, and then
  `option reset`
- **THEN** the preset and every option it set are back at the player's values

### Requirement: Option changes reach the server
An agent's change or reset of the client options SHALL reach the server as one
made in the game's settings does, so a new render distance changes how far
around the player the server sends chunks.

#### Scenario: Render distance raised in a world
- **WHEN** an agent who joined a world at render distance 6 runs
  `option renderDistance 20`
- **THEN** the server sends the chunks within 20 chunks of the player, as if
  the game had been started at 20

### Requirement: Supported options
The option names SHALL be those of the running version's `options.txt`:
`graphicsMode`, `particles`, `renderDistance`, `simulationDistance`,
`renderClouds`, `ao`, `entityShadows`, `biomeBlendRadius`, `mipmapLevels`,
`entityDistanceScaling`, `maxFps`, `enableVsync`, `prioritizeChunkUpdates`,
`fov`, `gamma`, `guiScale`, `bobView`, `fovEffectScale`, `screenEffectScale`,
`darknessEffectScale`, `damageTiltStrength`, `hideLightningFlashes`,
`glintStrength` and `glintSpeed`.

#### Scenario: Unknown option
- **WHEN** an agent runs `option shaderPack on`
- **THEN** the result is `err syntax` pointing to `option` for the list

### Requirement: The hud option
Besides those of `options.txt`, `hud` SHALL take `on` or `off` and show or hide
the GUI as the F1 switch does.

#### Scenario: GUI hidden for a probe
- **WHEN** an agent runs `option hud off; pixels`
- **THEN** the regions are measured without the GUI, as after pressing F1

### Requirement: Graphics options from 26.1
From 26.1 on, where Fast, Fancy and Fabulous became presets, `graphicsPreset`,
`cutoutLeaves` and `improvedTransparency` SHALL take the place of
`graphicsMode`.

#### Scenario: Graphics on 26.1
- **WHEN** an agent runs `option graphicsMode fast` on 26.1
- **THEN** the result is `err syntax`, and `option` lists `graphicsPreset`,
  `cutoutLeaves` and `improvedTransparency`

### Requirement: Option values
Choices SHALL be written by name in lower case (`fast`, `fancy`, `fabulous`),
switches as `on|off`, and numbers within the option's range. Anything else
SHALL fail with `err syntax` naming what the option accepts.

#### Scenario: Value out of range
- **WHEN** an agent runs `option renderDistance 99`
- **THEN** the result is `err syntax` naming the accepted range

### Requirement: Settling after an option change
After a change, `option` SHALL wait as `settle` does: it answers once every
chunk within the view distance of the player has arrived and the chunk compile
queue has stayed empty for 3 ticks, with `settled=<seconds>`, or at `timeout=`
(default 20s) with `unsettled pending=<n>`, and `missing=<n>` while chunks
have yet to arrive. Either way the option keeps its new value.

#### Scenario: Render distance raised
- **WHEN** an agent runs `option renderDistance 16`
- **THEN** the reply comes after the newly visible chunks have arrived and
  their sections have compiled, with `settled=` and the time it took

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
`inside=<block>`, and the camera is placed all the same. When the camera sits
in a chunk the client hasn't loaded, which renders as empty sky, the reply
SHALL carry `unloaded` instead of a block.

#### Scenario: Camera in a panel
- **WHEN** an agent runs `camera at <position inside an overhead panel>`
- **THEN** the reply carries `inside=` with the panel's block id, warning that
  the view starts inside a solid block

#### Scenario: Camera in the air
- **WHEN** an agent runs `camera at ~0,10,0`
- **THEN** the reply names `air` as the camera's block and carries no `inside=`

#### Scenario: Camera beyond the loaded chunks
- **WHEN** an agent runs `camera at <a point 2000 blocks from the player>`
- **THEN** the reply carries `unloaded` and names no block

### Requirement: Since as a tick or a duration
`since=` on `sounds`, `messages` and `particles` SHALL take either a tick
number, the first tick to include, or a duration such as `60t` or `3s`, which
includes the ticks that long before the current tick.

#### Scenario: The last three seconds
- **WHEN** one sound played 40 ticks ago and another 100 ticks ago, and an
  agent runs `sounds since=3s`
- **THEN** the first sound is listed and the second is not

#### Scenario: A tick from an earlier reply
- **WHEN** an agent runs `messages since=<tick>` with the `tick` of an
  earlier reply
- **THEN** the messages from that tick on are listed, as before

### Requirement: Waiting for the view to settle
`settle [timeout=<duration>]` SHALL answer once every chunk within the view
distance of the player, or of a camera placed with `away`, has arrived and
the chunk compile queue has stayed empty, with the same number of sections
rendered, for 3 ticks, with `settled=<seconds>`, or at `timeout=` (default
20s) with `unsettled pending=<n>`, plus `missing=<n>` while chunks have yet
to arrive. It SHALL change nothing.

#### Scenario: Camera moved far
- **WHEN** an agent runs `camera at ~150,0,0; settle; screenshot`
- **THEN** `settle` answers with `settled=` once the sections around the
  camera have compiled, and the screenshot shows them

#### Scenario: Nothing new in view
- **WHEN** an agent runs `settle` while the view hasn't changed
- **THEN** it answers with `settled=` after a few ticks

#### Scenario: New terrain still generating
- **WHEN** an agent raises the render distance where the terrain around the
  player isn't generated yet, and runs `settle timeout=120s`
- **THEN** `settle` answers once the chunks within the new view distance have
  arrived, so `block` at the edge of that distance finds its chunk loaded

#### Scenario: A camera away
- **WHEN** an agent runs `camera at 1000,-58,0 away; settle`
- **THEN** `settle` answers once the chunks within the view distance of the
  camera have arrived and compiled, not those around the player

### Requirement: Sounds playing now
`sounds playing [since=] [match=] [limit=]` SHALL list the sounds the client's
sound engine is playing: id, category, position or `ui`, volume, pitch,
`loop` for a looping sound, the tick it started and the seconds it has played,
oldest start first, with `count` and the current `tick`. `since=` keeps those
that started from that tick on. A sound that has ended, or that the engine
never started because its volume came out at 0, SHALL not be listed.

#### Scenario: Looping track still playing
- **WHEN** a mod started a looping track a minute ago and an agent runs
  `sounds playing match=*techno*`
- **THEN** a row lists the track with `loop` and about 60 s played, although
  the log may have dropped its start

#### Scenario: Waiting for a fade-out
- **WHEN** an agent runs `wait sounds playing match=*techno* until count==0
  timeout=30s` while the mod fades the track out
- **THEN** the wait returns once the track has stopped, or fails with `err
  timeout`

#### Scenario: Muted category
- **WHEN** the music volume is 0 and a mod starts a music track
- **THEN** `sounds` lists its start, and `sounds playing` does not list it

### Requirement: Log coverage
When the sound or the message log has dropped entries from the window a
`sounds` or `messages` query covers (from `since=` on, or the whole log
without it), the reply SHALL carry `from=<tick>`, the tick of the oldest entry
the log still keeps, so a short or empty list is not taken for all there was.

#### Scenario: Busy scene
- **WHEN** more than 2,000 sounds started since the tick an agent passes as
  `since=`
- **THEN** the `sounds` reply carries `from=` with the tick of the oldest start
  the log still keeps

#### Scenario: Nothing dropped
- **WHEN** an agent runs `sounds since=3s` and the log has dropped only starts
  older than that
- **THEN** the reply carries no `from=`

### Requirement: Unknown sound ids
A `sounds` `match=` without `*` SHALL be a sound event id, in `minecraft:`
when it names no namespace. When the client knows no sound event with that
id, the query SHALL fail with `err syntax unknown sound <id>`, naming up to
three of the closest ids it knows and the `*<name>*` form that matches the
name in any id.

#### Scenario: Bare name
- **WHEN** an agent runs `sounds match=techno` and a mod's sound events
  include `example:music.techno`
- **THEN** the result is `err syntax unknown sound minecraft:techno`, naming
  `example:music.techno` and `match=*techno*`

#### Scenario: Waiting on a misspelt id
- **WHEN** an agent runs `wait sounds match=minecraft:block.anvil.lnad until
  count>0 timeout=30s`
- **THEN** the wait fails at once with `err syntax unknown sound`, naming
  `minecraft:block.anvil.land`

### Requirement: Volume and pitch asked for
A row of `sounds` or `sounds playing` SHALL give as `vol` and `pitch` what the
sound plays with: the volume and pitch it was played with, times those its
resource (sounds.json) gives the variant chosen. When these differ from the
values it was played with, the row SHALL also carry those as
`asked:<volume>/<pitch>`, in the fields `asked_volume` and `asked_pitch`.

#### Scenario: Sound with variants
- **WHEN** a mod plays `minecraft:item.spyglass.use`, whose resource gives
  volume 0.5 and variants pitched 1.15 to 1.55, at volume 0.6 and pitch 1.2,
  and an agent runs `sounds match=item.spyglass.use`
- **THEN** the row has `vol0.3`, a pitch between 1.38 and 1.86, and
  `asked:0.6/1.2`

#### Scenario: Sound its resource leaves as played
- **WHEN** a sound whose resource sets neither volume nor pitch is played at
  volume 0.6 and pitch 1.2
- **THEN** its row has `vol0.6 pitch1.2` and no `asked:`

### Requirement: Placed camera in frame replies
While a camera placed with `camera at` is in effect, a `screenshot` reply
SHALL carry `camera <x,y,z>`, the camera's absolute position, for each frame
captured from it, and a `pixels` reply SHALL carry it for the frame it
measured. A `pixels` region SHALL NOT be named `camera`.

#### Scenario: Camera left in place
- **WHEN** an agent ran `camera at 40,90,-20` earlier and now runs `screenshot`
- **THEN** the reply carries `camera 40,90,-20`, and after `camera reset` a
  screenshot's reply carries none

#### Scenario: Region named camera
- **WHEN** an agent runs `pixels camera=0,0,1,1`
- **THEN** the result is `err syntax` saying `camera` is taken

### Requirement: The window a reply covers
`sounds`, `messages` and `particles` replies SHALL carry
`window=t<first>..t<now>`, the ticks their answer covers. The first tick is
the `since=` tick, or without `since=` where the default window starts: 20
ticks back for `particles`, the oldest entry for `sounds` and `messages`, or
the current tick when their log is empty. When the log has dropped entries
from that tick on, the first tick is the oldest tick it still holds instead.
The last is the current tick.

#### Scenario: A bare number read as ticks back
- **WHEN** an agent runs `particles since=100` at tick 14,159 and the particle
  log has dropped the counts before tick 12,959
- **THEN** the reply carries `window=t12959..t14159`, not the last 100 ticks

#### Scenario: A duration
- **WHEN** an agent runs `particles since=100t` at tick 14,159
- **THEN** the reply carries `window=t14059..t14159`

#### Scenario: No since
- **WHEN** an agent runs `particles` at tick 14,159
- **THEN** the reply carries `window=t14139..t14159`, the default last 20
  ticks

### Requirement: A since tick from before the world was joined
When `since=` on `sounds`, `sounds playing`, `messages` or `particles` is a
tick number earlier than the tick the player joined the current world, the
reply SHALL say so with `joined=t<tick>` and a reminder that a duration such
as `100t` counts back from now. The query SHALL still answer for the tick it
was given.

#### Scenario: Ticks back written as a tick
- **WHEN** the player joined at tick 12,890 and an agent runs `sounds
  since=100`
- **THEN** the reply lists the sounds from tick 100 on and carries
  `joined=t12890` with the reminder that `100t` counts back

#### Scenario: A tick from an earlier reply
- **WHEN** an agent runs `messages since=<tick>` with the `tick` of a reply
  in this world
- **THEN** the reply carries no `joined=`

### Requirement: Camera clear of blocks
With `clear`, `camera at` SHALL, when the point's cell holds a block with a
collision shape, move the camera along its view line toward the look point, a
quarter block at a time, to the first point whose cell has no collision shape,
at most 8 blocks and never past the look point. The reply SHALL give the
position used as `camera` and the distance moved as `cleared=<blocks>`. With no
such point in reach, the camera stays put and the reply carries `uncleared`.

#### Scenario: Inside a hull
- **WHEN** an agent runs `camera at <a point inside a hull block> look <a point
  in the cabin> clear`
- **THEN** the camera sits at the first point in the cabin past the hull block,
  and the reply carries `cleared=` with the distance and no `inside=`

#### Scenario: Already clear
- **WHEN** an agent runs `camera at ~0,10,0 clear` with air there
- **THEN** the camera sits at that point and the reply carries no `cleared=`

### Requirement: The sound volume options
Besides those of `Supported options`, `option` SHALL take the volume of each
sound category, from 0 to 1, by its name in the running version's
`options.txt`: `soundCategory_<category>` (`soundCategory_master`,
`soundCategory_music`, `soundCategory_block`, …).

#### Scenario: Music off for a take
- **WHEN** an agent runs `option soundCategory_music 0` while music plays,
  and later `option reset`
- **THEN** the music falls silent at once, and the reset brings back the
  player's music volume

### Requirement: Playing a sound on the client
`sound <id> [at <pos>] [volume=<v>] [pitch=<p>] [source=<category>]` SHALL
play the sound event on this client only, sending nothing to any server.
With `at`, it SHALL start there and fade with distance from the camera as
rendered; without, it SHALL play as an interface sound. `volume=` (default 1)
above 1 SHALL carry it farther, as with `playsound`; `pitch=` takes 0.5 to 2
(default 1), `source=` a sound category (default `master`).

#### Scenario: Heard from a placed camera
- **WHEN** an agent runs `camera at ~40,2,0; sound minecraft:block.bell.use
  at ~40,0,3`
- **THEN** `sounds playing match=block.bell.use` lists the bell at `~40,0,3`
  with `asked:1/1`, the volume and pitch it was played with, playing about
  3.6 blocks from the listener, which is the camera, not 40 from it

#### Scenario: An interface sound
- **WHEN** an agent runs `sound minecraft:ui.button.click`
- **THEN** `sounds` lists the click with `ui` for its position

#### Scenario: On a remote server
- **WHEN** an agent connected to a multiplayer server runs `sound
  minecraft:block.bell.use at ~0,0,3`
- **THEN** the bell plays on this client and nothing is sent to the server

### Requirement: What playing a sound answers
The reply to `sound` SHALL give the sound's id, its absolute position or
`ui`, its volume, pitch and category, and the tick it started, plus `muted`
when the volume of its category or the master volume is 0. An id the client
doesn't know SHALL fail as `sounds match=` does, with `err syntax unknown
sound <id>` naming the closest ids. A volume below 0 or a pitch outside 0.5
to 2 SHALL fail with `err syntax`.

#### Scenario: Muted category
- **WHEN** an agent runs `option soundCategory_block 0; sound
  minecraft:block.bell.use at ~0,0,3 source=block`
- **THEN** the reply carries `muted`, `sounds` lists the start, and `sounds
  playing` doesn't list it

#### Scenario: Unknown id
- **WHEN** an agent runs `sound minecraft:block.bell.uze`
- **THEN** the result is `err syntax unknown sound minecraft:block.bell.uze`
  naming `minecraft:block.bell.use`, and nothing plays

#### Scenario: Pitch out of range
- **WHEN** an agent runs `sound minecraft:block.bell.use pitch=3`
- **THEN** the result is `err syntax` naming the range 0.5 to 2

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

### Requirement: A camera away from the player
With `away`, `camera at` SHALL make the integrated server treat the camera as
where the player is for the world it loads, ticks and sends, while that camera
is placed: chunks within the view distance of the camera load and reach the
client, those near it tick and may spawn mobs, entities within tracking range of
it are sent, and sounds, particles and explosions within range of it play. How
near a player mobs spawn and despawn SHALL still go by the player.

#### Scenario: A set far from the player
- **WHEN** an agent standing at 0,-60,0 in a flat world runs
  `camera at 1000,-58,0 look 1000,-60,5 away; settle; block 1000,-61,0`
- **THEN** the camera's reply names `air` as its block, `settle` answers
  `settled=`, and `block` reads `grass_block`

#### Scenario: A sound at the set
- **WHEN** an agent runs `server playsound minecraft:block.bell.use master @a
  1000 -60 5` with that camera placed
- **THEN** `sounds match=block.bell.use` lists the bell at `1000,-60,5`, not
  next to the player

#### Scenario: An entity at the set
- **WHEN** an agent runs `server summon minecraft:lightning_bolt 1000 -60 5`
  with that camera placed
- **THEN** `sounds` lists `entity.lightning_bolt.thunder`, which the bolt
  sounds only on a client that it reached

#### Scenario: Particles at the set
- **WHEN** an agent runs `server particle minecraft:flame 1000 -59 5 0.2 0.2
  0.2 0 50` with that camera placed
- **THEN** `particles` counts flame particles

### Requirement: An away camera answers once its chunk has come
`camera at … away` SHALL answer once the client has the camera's chunk, or
after 10 seconds, and a camera move from a camera placed with `away` SHALL
keep it away, so the server's world follows the camera along the move.

#### Scenario: A move from the set
- **WHEN** an agent runs `camera move 1400,-58,0 look 1400,-60,5 over 40t;
  settle; block 1400,-61,0` with that camera placed
- **THEN** the move's reply names `air` as its block, and `block` reads
  `grass_block`: the server's world followed the camera along the move

### Requirement: The player waits while the camera is away
While a camera placed with `away` is farther from the player than the view
distance, the server SHALL send nothing around the player, and the player
SHALL stand where it was, neither moving nor falling, until the camera
resets; then the server SHALL send the chunks around the player again.

#### Scenario: Standing still
- **WHEN** an agent runs `status; wait 1s; status` with a camera placed 1000
  blocks away with `away`
- **THEN** both `status` replies give the same position

#### Scenario: Back to the player
- **WHEN** an agent runs `camera reset; settle; block ~0,-1,0`
- **THEN** `settle` answers `settled=`, `block` reads the block under the
  player, and the player moves again

### Requirement: What ends a camera away
A camera placed with `away` SHALL stop moving the server's world when it is
reset or turned off, when `camera at` places a camera without `away`, when
the player leaves the world or changes dimension, and when agent control is
turned off. `camera at … away` SHALL fail with `err no_server` without an
integrated server and with `err no_cheats` where commands aren't allowed.

#### Scenario: Placed again without away
- **WHEN** an agent runs `camera at ~5,2,0` after a camera placed with `away`
- **THEN** the server sends the chunks around the player again, and the new
  camera renders from `~5,2,0`

#### Scenario: On a remote server
- **WHEN** an agent connected to a multiplayer server runs `camera at
  1000,70,0 away`
- **THEN** the result is `err no_server`, and no camera is placed

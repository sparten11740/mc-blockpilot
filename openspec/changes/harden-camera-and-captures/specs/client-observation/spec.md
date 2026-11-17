# Spec Delta

## ADDED Requirements

### Requirement: What a shot list checks up front
Before anything changes, `camera play` SHALL check each shot as its command
would: option ranges, durations, offsets, server gates and that its entities
and sub-levels exist now, leaving out only what can change while it plays.
Names in shots SHALL be resolved as the play arrives. A play that fails the
check SHALL change nothing: the camera, and any play already running, stay as
they were.

#### Scenario: A bad shot after good ones
- **WHEN** an agent runs `camera play at ~0,2,0 | move ~1,0,0 over 0t`
- **THEN** the result is `err syntax`, naming the second shot, and the camera
  doesn't change

#### Scenario: A rejected play leaves the one playing
- **WHEN** a play of two 40-tick moves runs and an agent runs `camera play
  follow e999999`
- **THEN** the result is `err no_entity e999999`, and the first play goes on
  to its end

### Requirement: The hud option during a capture
While `screenshot` or `record` shows the HUD its own way with `hud=off` or
`hud=clean`, `option hud` SHALL read and set the HUD as the capture will
leave it, and what the capture changes SHALL not count as an agent change, so
restoring agent changes never leaves the HUD hidden or the field of view
effects as a capture set them.

#### Scenario: Reading the option during a clean recording
- **WHEN** the HUD shows and an agent runs `record start hud=clean; option
  hud; record stop`
- **THEN** `option hud` reads `on`

#### Scenario: Restoring after a change during a capture
- **WHEN** the HUD shows and an agent runs `record start hud=clean; option
  hud off; record stop; option reset`
- **THEN** the HUD shows and `option fovEffectScale` reads as it did before
  the recording

### Requirement: Clean frames leave the game alone
Clean frames SHALL change only what is drawn: the local player's effects
SHALL still act on the game while they don't show, and no other option SHALL
change, `fovEffectScale` keeping its value while the field of view holds
still.

#### Scenario: Blind and clean, still unable to sprint
- **WHEN** the player is blind and an agent runs `option hud clean; sprint
  on` and walks forward for a second
- **THEN** the player doesn't sprint, as without clean frames

#### Scenario: The option keeps its value
- **WHEN** an agent runs `option fovEffectScale 0.5; option hud clean; option
  fovEffectScale`
- **THEN** the last reply reads `0.5`

## MODIFIED Requirements

### Requirement: Screenshot bursts
`screenshot burst=<n> every=<Nt> [width] [hud=off]` SHALL capture n frames (at
most 60), one every N ticks, starting with the next frame, and return the saved
files in order with the tick of each. `hud=off` hides the GUI for the captured
frames. No frame SHALL be captured twice: a capture that falls due before a new
frame was drawn SHALL wait for the next, and each frame SHALL carry when the
client drew it, `drawn=`, on the client tick count that sample rows carry as
`client_tick`, and `late=<n>t` when it came after its time.

#### Scenario: Gear animation
- **WHEN** an agent runs `use; screenshot burst=8 every=20t` on the gear lever
- **THEN** eight PNG files covering 8 seconds of the animation are listed, each
  with its tick

#### Scenario: Frames slower than ticks
- **WHEN** an agent runs `load client 150; screenshot burst=4 every=1t`
- **THEN** the four frames have rising `drawn=` times, and those that waited
  for a frame carry `late=`

#### Scenario: Drawn on the samples' clock
- **WHEN** an agent runs `screenshot burst=3 every=1t; sample status every 1t
  x1`
- **THEN** each frame's `drawn=` is below the row's `client_tick` and within
  5 ticks of it

### Requirement: The hud option
Besides those of `options.txt`, `hud` SHALL take `on`, `off` or `clean`: `on`
and `off` show or hide the GUI as the F1 switch does, and `clean` makes frames
clean.

#### Scenario: GUI hidden for a probe
- **WHEN** an agent runs `option hud off; pixels`
- **THEN** the regions are measured without the GUI, as after pressing F1

#### Scenario: Clean as an option value
- **WHEN** an agent runs `option hud clean; option hud`
- **THEN** the second reply reads `clean`

### Requirement: Ending a shot list
Another camera command that passes its checks, `stop`, real input or a reset
SHALL end a play, leaving the camera where it is drawn. `camera wait` SHALL
answer once the last shot has ended, or with the error of a shot that
couldn't start, such as one whose entity is gone, however it failed. That
error SHALL be told once, to the next `camera wait`; another camera command
that passes its checks clears it.

#### Scenario: A camera at in the middle
- **WHEN** a play of two 60-tick moves runs and an agent runs `camera at
  ~0,2,-4` after 20 ticks
- **THEN** the camera stays at that point, and no later shot moves it

#### Scenario: A camera command that fails
- **WHEN** a play of two 60-tick moves runs and an agent runs `camera at
  ~0,2,-4 fov=500` after 20 ticks
- **THEN** the result is `err syntax`, and the play goes on to its end

#### Scenario: A shot that can't start
- **WHEN** an agent runs `server summon armor_stand ~2 ~ ~2
  {Tags:["extra"]}; wait entity @extra until ok; camera play hold 20t |
  follow @extra; server kill @e[tag=extra]; camera wait`
- **THEN** the play answers at once, and `camera wait` answers `err
  no_entity` naming the stand's `e<id>`

#### Scenario: The error is told once
- **WHEN** an agent runs `camera wait` again after that
- **THEN** it answers `ok` at once

### Requirement: Flicker of parts of the frame
`flicker <duration> <parts>…` SHALL read the mean luma of each part, taken as
`pixels` takes it, in every frame drawn over the duration (2t to 10s), and
give each part's mean, min, max and strongest rhythm as `flicker=<hz>` in
cycles per game second, with `period=` and `amplitude=`, or `flicker=none`
when no rhythm of at least 1 luma repeats at least twice in the frames, and a
row per frame. With fewer than two frames drawn over the duration, it SHALL
fail with `err no_frames`.

#### Scenario: A block swapped every 2 ticks
- **WHEN** a command-block clock swaps a block between glowstone and black
  concrete every 2 ticks and an agent runs `flicker 2s lamp=@<block> r=0.4
  sky=0.05,0.05,0.2,0.2`
- **THEN** the lamp reads `flicker=5hz period=4t`, and the sky
  `flicker=none`

#### Scenario: A lamp switched on once
- **WHEN** a redstone lamp is lit half way through `flicker 2s
  lamp=@<block> r=0.4`
- **THEN** the lamp reads `flicker=none`, with its min and max far apart

## MODIFIED Requirements

### Requirement: Screenshot bursts
`screenshot burst=<n> every=<Nt> [width] [hud=off]` SHALL capture n frames (at
most 60), one every N ticks, starting with the next frame, and return the saved
files in order with the tick of each. `hud=off` hides the GUI for the captured
frames. No frame SHALL be captured twice: a capture that falls due before a new
frame was drawn SHALL wait for the next, and each frame SHALL carry when the
client drew it, `drawn=`, and `late=<n>t` when it came after its time.

#### Scenario: Gear animation
- **WHEN** an agent runs `use; screenshot burst=8 every=20t` on the gear lever
- **THEN** eight PNG files covering 8 seconds of the animation are listed, each
  with its tick

#### Scenario: Frames slower than ticks
- **WHEN** an agent runs `load client 150; screenshot burst=4 every=1t`
- **THEN** the four frames have rising `drawn=` times, and those that waited
  for a frame carry `late=`

### Requirement: Screenshot crops and contact sheets
`screenshot` SHALL accept `crop=<x0>,<y0>,<x1>,<y1>`, fractions of the frame
from the top left, and capture only that part of each frame, before scaling
to the width. With `burst=<n>` of at least 2, `sheet` SHALL save the
frames as one PNG instead: a grid with `ceil(sqrt(n))` columns, each tile
labelled with its frame number, tick and drawn time (`#3 t1234 c812.4`), and
tiles scaled so the sheet is at most 1,536 pixels wide. The reply gives the
sheet's file and one row per frame with its tick.

#### Scenario: Gear retraction in one image
- **WHEN** an agent runs `screenshot burst=8 every=20t sheet
  crop=0.3,0.3,0.7,0.7`
- **THEN** one PNG holds the eight cropped frames in a three-column grid, each
  labelled with its tick

#### Scenario: Sheet without a burst
- **WHEN** an agent runs `screenshot sheet`
- **THEN** the result is `err syntax`, because a sheet needs `burst=<n>` of at
  least 2

## ADDED Requirements

### Requirement: Clean frames
`option hud clean`, and `hud=clean` on `screenshot` and `record`, SHALL hide
the HUD as `off` does and also the whole HUD pass, with the layers and
overlays other mods draw in it; the local player's night vision, blindness,
darkness and nausea SHALL not show, and the field of view effects SHALL stay
still. `option reset` and the end of a capture SHALL put it back.

#### Scenario: Blindness in a clean frame
- **WHEN** the player is blind and an agent runs `option hud clean; pixels
  sky=0.3,0.05,0.7,0.2`
- **THEN** the sky reads as bright as without blindness, where with `option
  hud off` it reads black

### Requirement: Flicker of parts of the frame
`flicker <duration> <parts>…` SHALL read the mean luma of each part, taken as
`pixels` takes it, in every frame drawn over the duration (2t to 10s), and
give each part's mean, min, max and strongest rhythm as `flicker=<hz>` in
cycles per game second, with `period=` and `amplitude=`, or `flicker=none`
when it changes by less than 1 luma, and a row per frame.

#### Scenario: A block swapped every 2 ticks
- **WHEN** a command-block clock swaps a block between glowstone and black
  concrete every 2 ticks and an agent runs `flicker 2s lamp=@<block> r=0.4
  sky=0.05,0.05,0.2,0.2`
- **THEN** the lamp reads `flicker=5hz period=4t`, and the sky
  `flicker=none`

### Requirement: Particles by type
`particles match=<pattern>` SHALL keep the particle types that match, an id
without `*` naming one type (`minecraft:` when it names no namespace), and
`total` SHALL count only those.

#### Scenario: Flames among hearts
- **WHEN** flames and hearts spawn and an agent runs `particles since=10t
  match=flame`
- **THEN** the reply lists the flames and no hearts

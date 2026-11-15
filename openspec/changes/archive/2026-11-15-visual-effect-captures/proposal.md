# Proposal

## Why

Developing an animation, an explosion, a lamp or a spinning ring, through
Blockpilot needs captures a model can judge motion from. A day of tuning
effects showed four gaps: `screenshot burst` returned the same frame several
times when the server hitched, labelled with the same tick; `hud=off` still
showed the screen overlays other mods draw and the player's effects tinted
every shot; a strobing lamp could only be judged by eye, as nothing measured
how a region's brightness changes from frame to frame; and `particles` had no
filter, unlike `sounds`.

## What Changes

- `screenshot burst` captures each frame only once: a capture that falls due
  before a new frame is drawn waits for the next one. Each frame carries when
  the client drew it, `drawn=<client tick>.<fraction>`, and `late=<n>t` when it
  came after its time; a sheet's labels add the drawn time.
- `clean`, a third HUD setting beside `on` and `off`: `option hud clean`, and
  `hud=clean` on `screenshot` and `record`, hide the whole HUD pass, other
  mods' layers and overlays included, and the player's own effects stop
  changing how the world is drawn (night vision, blindness, darkness, nausea
  and the field of view effects).
- `flicker <region>… over <duration>`: the mean brightness of each region in
  every frame drawn over the duration, with each region's dominant flicker
  frequency in hertz, its amplitude and the frame rate; the regions as
  `pixels` takes them.
- `particles match=<pattern>` keeps the matching particle types, as `sounds
  match=` does.
- docs/recording.md gains a recipe for testing visual effects (a slow tick
  rate, a fixed camera, a clean burst sheet, `flicker`, a snapshot restored
  between runs), and `help server` says `@s` is the server unless
  `as=player`.

Out of scope: per-frame `every=<n>f` bursts, millisecond spacing, and read-only
commands that real input doesn't interrupt.

## Capabilities

### New Capabilities

None.

### Modified Capabilities
- `client-observation`: bursts without repeated frames, the clean HUD
  setting, `flicker`, and `particles match=`.

## Impact

- **Capture:** `ObservationCommands` (burst, particles, flicker),
  `Screenshots` (when each frame was drawn), `HudVisibility` and
  `ClientOptions` (clean), `RecordCommands` (`hud=clean`).
- **Mixins:** the HUD pass and the player's effects while a frame is drawn,
  per version.
- **Docs:** help, docs/commands.md, docs/recording.md, README, CHANGELOG under
  1.4.0.

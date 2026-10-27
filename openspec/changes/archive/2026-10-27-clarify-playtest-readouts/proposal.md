# Proposal

## Why

Two readouts can mislead an agent that checks a mod's work. `sounds` reports a
sound's volume and pitch after the sound's resource has scaled them: vanilla's
sounds.json gives `item.spyglass.use` volume 0.5 and four variants pitched
1.15 to 1.55. So a sound played at volume 0.6 and pitch 1.2 is listed as
`vol0.3` with a pitch of up to 1.86, and a test can't tell which pitch the
code chose. A camera placed with `camera at` stays until `camera reset`, and
nothing in a screenshot or pixel reply says so. An agent that forgot it took
outside views for the player's own.

## What Changes

- `sounds` and `sounds playing` keep `vol` and `pitch` as what plays: the
  values the sound was played with, times the volume and pitch its resource
  gives the variant chosen. When these differ from the values it was played
  with, a row also carries those as `asked:<volume>/<pitch>` (fields
  `asked_volume` and `asked_pitch`). `help sounds` says which is which, and
  that the engine plays pitch within 0.5 to 2.
- While a placed camera is in effect, `screenshot` replies carry `camera
  <x,y,z>`, the camera's absolute position, for each frame captured from it,
  and `pixels` replies carry it for the frame they measured. A `pixels`
  region can't be named `camera`.

## Capabilities

### New Capabilities

None.

### Modified Capabilities
- `client-observation`: the values a sound was played with, and the placed
  camera named in screenshot and pixel replies.

## Impact

- `game/observe/Observations.java` (the values a sound was played with),
  `game/observe/ObservationCommands.java` (sound rows, `screenshot`, help),
  `game/observe/PixelCommands.java`, `game/observe/CameraControl.java` (the
  camera of the last rendered frame), and a new accessor mixin for the sound
  instance's own volume and pitch in both version source sets and both
  `blockpilot.mixins.json`.
- README: the observe row.

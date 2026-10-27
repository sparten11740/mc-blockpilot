# Design

## Context

- A mixin on `SoundEngine.play` logs each start once the sound has resolved.
  The log, and the rows of `sounds playing`, take `SoundInstance.getVolume()`
  and `getPitch()`. For `AbstractSoundInstance`, which all vanilla sounds
  extend, these return its protected `volume` and `pitch` fields times the
  values the chosen variant's resource gives (`Sound.getVolume()` and
  `getPitch()`, constants read from sounds.json). The fields and the methods
  are the same in 1.21.1 and 26.1. The engine then plays pitch within 0.5 to
  2.
- `CameraControl` holds the placed camera. Its position may move with an
  entity or a block aboard a sub-level. `recordRendered()` runs once per frame
  after the level has rendered and records the camera's angles.
- `screenshot` captures frames rendered after the request; `pixels` reads the
  last rendered frame at once. `camera reset` answers without waiting for a
  frame, so the last rendered frame can still be the placed camera's.

## Goals / Non-Goals

**Goals:**
- Existing `vol` and `pitch` keep their meaning, so waits and asserts on
  them keep working.

**Non-Goals:**
- Naming the variant (sound file) the engine chose; the asked values are
  enough to check what the code chose.
- A note on the MCP `screenshot` tool's reply, which carries only the image.

## Decisions

### D1. The asked values come from the instance's own fields

An accessor mixin on `AbstractSoundInstance` in each version source set reads
`volume` and `pitch`. The play hook records them with the start, since a
tickable sound may change them later (a fade). `sounds playing` reads them at
query time, as it does the played values. A sound instance that doesn't
extend `AbstractSoundInstance` has no asked values, and its row shows none.

Alternative: divide the played values by the variant's resource values. That
needs the variant and its random sample, and fails on a zero volume.

Alternative: make `vol` and `pitch` the asked values and add the played ones.
That changes the meaning of fields that tests may already use.

### D2. Asked values only where they differ

The row carries `asked:` only when the asked volume or pitch differs from the
played one. A resource that sets neither leaves the product equal to the
asked value exactly (x times 1.0), so most rows stay as they are. `help
sounds` says what `vol` and `pitch` are, and that the engine plays pitch
within 0.5 to 2.

### D3. The camera of the frame, not of the moment

`recordRendered()` also records the placed camera's position used for that
frame, or none. `screenshot` adds `camera <x,y,z>` to each frame captured
from a placed camera, and to the reply of a single frame. `pixels` adds it
for the frame it measured. Reading `CameraControl.placement()` at reply time
instead would be wrong right after `camera reset`, when the last rendered
frame is still the placed camera's.

### D4. `camera` is a reserved region name

`pixels` reply fields are named after the agent's regions. A region named
`camera` would clash with the new field, so it fails with `err syntax`.

## Risks / Trade-offs

- [A mod's own sound instance that implements `SoundInstance` directly] →
  its rows lack `asked:`; `vol` and `pitch` stay right.
- [A region named `camera` in existing scripts now fails] → the error says
  the name is taken; any other name works.

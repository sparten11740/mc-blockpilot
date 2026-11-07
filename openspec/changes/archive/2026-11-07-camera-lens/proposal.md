# Proposal

## Why

A placed camera sees with the player's field of view and never tilts. A
shot often wants a narrow view, a face filling the frame from across a
valley, or a wide one for a whole set, a zoom that tightens along a move, or
a tilted horizon for a moment of unease. `option fov` changes the player's
own setting in whole degrees from 30 to 110 and jumps from one value to the
next, and nothing tilts the view at all.

## What Changes

- `fov=<degrees>` and `roll=<degrees>` on the `camera` forms that place a
  camera. `fov=` sets the placed camera's vertical field of view, from 1 to
  170 degrees, in place of the player's, sprinting and potion effects
  included; `roll=` tilts it about the way it looks, from -180 to 180
  degrees, a positive roll leaning it to its right.
- `camera at` and `camera follow` set them at once; `camera move` and
  `camera orbit` glide from the values in use to the given ones along the
  motion, with its `ease=`. A value left out stays as it is, and
  `camera reset` returns to the player's view.
- `status` names a placed camera's own field of view as `cam_fov`, and its
  roll in `cam:<yaw>/<pitch>/<roll>`, which shows a loader's camera roll
  today and only NeoForge's.
- The help, `docs/commands.md`, the README and the 1.1.0 section of
  `CHANGELOG.md` describe both options.

It builds on `camera-follows-and-orbits` for `follow`, `orbit` and the
timed-motion base the glide uses.

## Capabilities

### New Capabilities

None.

### Modified Capabilities
- `client-observation`: a placed camera's field of view and roll.

## Impact

- `game/observe/CameraControl.java`: a `Lens` beside the `Placement`'s
  position and view, the field of view and roll for a frame.
- `game/observe/CameraMotion.java`: the glide from one lens to another.
- `game/observe/CameraCommands.java`: the options, their ranges, the help.
- Mixins for both versions: the field of view the world is drawn with, and
  the roll of the camera's rotation, for a placed camera with its own;
  `CameraControl.recordRendered` reports the lens's roll.
- `game/PerceptionCommands.java`: `cam_fov` in `status`.
- `tools/smoke_test.py`: checks that a narrow view fills the frame with a
  far block, that a roll puts the sky to one side, and that a move glides
  the field of view.
- `docs/commands.md`, `README.md`, `CHANGELOG.md`.

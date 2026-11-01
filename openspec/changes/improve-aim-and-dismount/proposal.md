# Proposal

## Why

A play-test aboard a fast sub-level turned up one bug and three gaps. The bug:
`camera at <plot position> clear` with no `look` steps from a plot point
toward the player's eyes in world coordinates, millions of blocks away, so it
moves the camera along a meaningless line. The same happens with any look
point outside the camera point's own frame. The gaps:
- `camera`, `face` and `use` already follow a sub-level for a plot position,
  but their help doesn't say so. The tester assumed world positions and built
  workarounds.
- `face <pos>` that ends on `aim:nothing` doesn't say whether the target is
  beyond the player's reach or the crosshair rests on empty air within it.
- `sneak` needs `on` or `off`. A bare `sneak`, as for dismounting a seat, is
  `err syntax`. That is easy to miss when the output is discarded, and a tap
  takes two commands.

## What Changes

- `camera at … clear` puts the look point into the camera point's frame
  before stepping: the camera's plot when the point is aboard a sub-level,
  else the world. This lifts the earlier non-goal of not clearing a camera
  aboard a sub-level that looks at a point in the world.
- The `camera`, `face` and `use` help say that, with Sable, a plot position
  follows its sub-level on each frame (`camera`) or tick (`face`, `use`).
- `face` that ends on `aim:nothing` adds `dist=<blocks>`, from the eyes to the
  point it aimed at, and `reach=<blocks>`, the player's interaction range for
  that kind of target.
- A bare `sneak` taps sneak: it holds it for a few ticks and lets go, which
  dismounts a player from a seat or vehicle. `sneak on` and `sneak off` are
  unchanged.

## Capabilities

### New Capabilities

None.

### Modified Capabilities
- `player-control`: the bare `sneak` tap; `face`'s `dist=` and `reach=` on
  `aim:nothing`.
- `sable-sublevels`: `camera … clear` in the camera point's frame; the
  `camera`, `face` and `use` help name plot positions.

## Impact

- `game/ActionCommands.java` (`face`, `use` help, `sneak`),
  `game/observe/ObservationCommands.java` (`camera at … clear`, `camera`
  help).
- README: the movement row and the Sable section.

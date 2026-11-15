# Design

## Context

A placed camera is a `Placement` of `Point`s, functions of the partial tick
that give where something is drawn in a frame. `CameraControl.drawn` turns a
plot position into a point that rides its sub-level; `CameraFollow` keeps an
offset from an entity, in the world's or the entity's frame. A sub-level's
pose gives its origin in the world (`SubLevels.Info.position`); the Sable
layer maps plot and world points and gives where the client draws a plot
point (`toWorldRendered`). The `sable:` commands resolve a sub-level from its
UUID, its name or `here`.

## Decisions

### D1: `sub:<ref>` as a slot type of its own

A token with the prefix `sub:` reads like `e<id>` and `@<name>` beside the
camera's other targets, and keeps names with capitals, which an id can't
hold. It needs a slot type, which lives in the extension API, so
`ArgType.SUB_LEVEL` is an addition there and the API becomes 2.2.0. The
token is also an id in the namespace `sub`; the types declare the overlap,
and no slot takes both.

Alternatives: a keyword, `camera follow sub ship`, needs no API change but
reads unlike the other targets and adds a word to each form; an id with the
namespace `sub` loses names with capitals.

### D2: The origin as a plot point drawn each frame

The origin's plot point, taken once from the pose, is drawn each frame with
`drawn`, which already turns a plot point into where the client draws it and
holds the last place once the sub-level is gone. A follow, orbit, look or
path point is then a function of that point, so the camera tracks the ship
as drawn, as entity follows do; `sable:info` reads the client's pose a tick
or so ahead of what is drawn.

### D3: A world-axis follow of any point

`CameraFollow.placement(Point origin, Vec offset)` keeps the offset from any
moving point and looks at it. Entities keep their own follow, which reads
their body's heading for `turning`; a sub-level refuses `turning`, as a camera
at a plot position already turns with it.

### D4: `around` makes relative points offsets from the origin

With `around`, a relative position of the move (its point, via points and a
positional look) becomes the origin plus that offset in each frame; absolute
and plot positions keep their meaning. The move answers where the camera
rests at its end, since the end moves with the sub-level.

## Risks / Trade-offs

- [A sub-level's origin is its pose's position, which may not be its visual
  centre] → offsets are given from it, and `sable:info` shows it.
- [Without Sable the slot still parses] → it answers `err unsupported`
  naming Sable, as the smoke test checks on every target.

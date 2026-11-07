# Design

## Context

- `CameraControl` holds a `Placement`: a `Point` for where the camera sits,
  a `Point` it looks at and an optional `View`, each worked out for the
  frame being drawn, so an entity is read where that frame draws it.
- `CameraMove` is the only timed motion: it counts `CameraControl`'s client
  ticks, runs in the foreground or the background, ends at its last point
  and freezes where the last frame drew it on `stop`. `CameraControl`
  holds it as the move under way, and `camera wait`, `stop`, real input and
  a newer placement act on that.
- `camera` and its helpers live in `ObservationCommands` with screenshots,
  sounds, messages, particles and `settle`, about 800 lines; `rendered`,
  which turns a world point into one a frame can read, serves `settle` too.
- A look at an entity aims at its position plus half its height, read where
  each frame draws it (`lookTarget`).
- `background` and `camera wait` come from `camera-moves-in-the-background`,
  and `@<name>` from `entities-by-name`, both still active changes.

## Goals / Non-Goals

**Goals:**
- A camera that goes along with an entity, keeping its framing.
- A circle round a point or a moving entity, timed like a move.

**Non-Goals:**
- A camera that keeps a distance but lags or dodges blocks like a game's
  third-person camera; `clear` covers placing one clear of blocks.
- Following along a path of the entity's own past positions.
- Changing the radius or height along an orbit; two orbits or a move do.

## Decisions

### D1: The camera command gets its own class

`camera`'s declaration, handler and helpers move to `CameraCommands`,
unchanged, in a commit of their own before anything is added, and
`ObservationCommands.register` registers it where it did, so the help's
order and the generated grammar stay as they are. `rendered`, which both
classes use, moves to `CameraControl` as a package-private helper rather
than being copied.

### D2: Timed motions share one base

`CameraMotion`, abstract, holds what a timed motion needs whatever its
shape: the start tick and duration, the progress for a frame, the ease,
`background`, `finished`, `tick` placing the end and answering as
`camera at`, and `cancel` freezing the camera where the last frame drew it.
A subclass gives the position and view for a progress and its end
placement. `CameraMove` keeps its path, via points and look; `CameraOrbit`
its circle. `CameraControl.moving` holds a `CameraMotion`, so `camera
wait`, `stop`, real input and a newer placement need no change to cover an
orbit, and a further timed motion is one more subclass. The handler's guard
that keeps `ease=` to `camera move` lets `orbit` take it too. The forms
keep each form's words to itself, so `via` or `look` on `follow`, or `at` or
`turning` on `orbit`, are `err syntax` from the parser.

### D3: An orbit keeps the camera's distance and height

At its start the orbit reads where the camera is drawn and where the centre is,
for an entity the point a look at it aims at (`lookTarget`), and keeps the
horizontal distance between them, the height above the centre and the bearing
from it. In a frame at eased progress `p` the camera sits at the centre for that
frame plus the start offset turned by `p` times the angle about the vertical,
and looks at the centre. A positive angle moves the camera to its right as it
faces the centre. At the end it rests where the orbit put it, looking at the
centre as `camera at … look` would, so an entity centre is looked at but no
longer followed. `by 0` holds the camera where it is for the duration, as a
timed pause. When an entity centre is removed, the orbit holds the camera
where the last frame drew it and still answers at its time, as a follow
does (D4). The turned offset is a pure function beside `CameraPath`'s, with
unit tests.

### D4: A follow is a placement

`camera follow` places a `Placement` whose position is the entity's
position for the frame plus the offset and whose target is the point a look
at the entity aims at (`lookTarget`), so a follow after `camera at … look`
keeps the same aim. It lasts until the next `camera` command like `camera
at`; `stop` and real input end actions and moves under way, and a follow is
neither, so it stays. Without `at` the offset is where
the camera is drawn now less the entity's position. A removed entity
leaves the camera where it was last drawn, looking the same way.

### D5: Turning follows a smoothed heading

With `turning` the offset is in the entity's frame: `x` to its left, `y`
up, `z` the way it faces, as Minecraft's `^` coordinates. The heading is
the entity's body yaw for a living entity, else its yaw, and the camera's
heading approaches it with a time constant of 10 client ticks, counted on
`CameraControl`'s clock with the frame's partial tick, so a quick turn
swings the camera round over a second or two (95% of the turn in 1.5 s)
instead of in one frame.
The smoothed heading moves only when that clock has, so the several reads
of one frame agree. Without `at`, the offset is read in the entity's frame
at the start.

## Risks / Trade-offs

- [The smoothed heading lags a steady turn] → The camera trails a circling
  entity by a little; a scene that needs it locked uses no `turning` or an
  orbit.
- [An orbit centred under the camera has no bearing] → Its radius is zero,
  so the camera keeps its position and looks at the centre; the help says
  an orbit keeps the distance the camera has.
- [`camera` grows two forms and its help grows longer] → The help gives each
  form its own clause, as it does for `at` and `move`.

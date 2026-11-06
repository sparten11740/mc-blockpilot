# Design

## Context

- `camera at` stores a `CameraControl.Placement`: two `Point`s, the
  position and the look target, each a function of the frame's partial
  tick. The camera mixins (`src/1.21.1` and `src/26.1`) ask the placement
  for both in every rendered frame, after the camera has followed its entity
  and before the frustum is worked out, then set the position and the yaw
  and pitch toward the target. A placed camera is already drawn per frame;
  only what it is told changes once a tick.
- A plot position becomes a `Point` that asks Sable where the client draws
  that plot point in this frame (`toWorldRendered`), which is how a camera
  rides a ship. An entity target is the centre of its bounding box, which
  moves once a tick.
- `CameraControl.recordRendered` runs once per frame after the level
  rendered and keeps the camera's angles and, for a placed camera, its
  position; `screenshot` replies name that position as `camera`.
- `camera at` answers after two client ticks (`ActionSupport.afterFrames`),
  so the frame a following `screenshot` reads is from the new place.
- `record` takes the newest rendered frame at each `1/fps` of wall clock.
  While it runs the idle frame-rate limit is off, so the game renders up to
  `maxFps`, usually above the video's rate.
- Actions run one client tick per step and get the 60 s timeout unless they
  time themselves (`Engine.SelfTimed`). `stop` and real input cancel the
  running action through `Action.cancel`.
- A `Form` (in the extension API) is keywords, slots and optional groups;
  a group is taken once or not at all, and `Args` holds one value per slot
  name. Nothing in the language can take a slot several times.

## Goals / Non-Goals

**Goals:**
- A camera that is in a new place in every rendered frame of a move, on
  every target, with no change to the mixins.
- Curved paths through given points with no corner anywhere, at a speed the
  agent can predict from the path's length and the duration.
- A move takes the time asked, so it lines up with what the script makes
  happen in the world over the same ticks.

**Non-Goals:**
- Control handles, tension or other shaping of the curve beyond its points.
- `clear` on a move, and moving the field of view or roll.
- Keeping chunks loaded around a distant camera; a separate proposal.
- Changing `camera at`'s reply or what `record` captures.

## Decisions

### D1: The move is a placement whose points follow its progress

`camera move` places a `Placement` whose position `Point` works out the
move's progress for the frame and returns the point that far along the path.
The path's points are `Point`s themselves: the start is the old placement's
position, or the player's eye position at the frame's partial tick when no
camera was placed; each via point and the end are `rendered(level, pos)`, as
for `camera at`. Every frame evaluates each of them once and builds the path
from where they are drawn now, so a path from a riding camera, through a
point on a ship, or from the player's eyes, follows all of them.

Alternatives:
- Update the placement once a client tick: the steps this change removes.
- A new mixin hook that moves the camera: the placement already runs every
  frame on both mixins, and a second path would have to agree with it.

### D2: Progress runs on the client's ticks plus the frame's real partial tick

Progress is `(ticks since the move started + partial tick) / duration`,
where the ticks are Blockpilot's client tick count and the partial tick is
the game timer's fraction of the current tick, read so that tick freeze
doesn't hold it. A move of `40t` takes 40 client ticks, the same clock as
`wait 40t`, so it lines up with steps around it. It goes on through a
frozen world, which suits a shot that flies through a frozen moment. The
moving points (a ship, an entity) are still read at the partial tick the
frame draws the world at, so they stay where the world shows them.

Alternatives:
- The wall clock: smooth too, and what `record` uses, but a lagging game
  would let the camera run ahead of the world it films, and a script
  couldn't line a move up with ticks.
- The game time: it stops under `tick freeze` and repeats or skips under
  load, so the camera would stall or jump with it.

### D3: A centripetal Catmull-Rom curve through the points

The path runs through the start, the via points in order and the end as a
centripetal Catmull-Rom spline (knots spaced by the square root of the
distance between points). Each span between two points is a cubic shaped by
the points either side, so the curve passes through every point and its
direction doesn't jump at any of them. The first and last spans get a
mirrored point beyond their end, so the curve leaves the start and reaches
the end heading for its neighbour. Points closer than a hundredth of a block
to the one before are dropped, which also keeps the knot spacing above zero.
Without via points the path is the straight line, as before.

Alternatives:
- A polyline through the points: the corners at every point this change is
  meant to remove.
- Uniform Catmull-Rom: with unevenly spaced points it overshoots into loops
  and cusps; chordal spacing swings wide round bends. Centripetal spacing
  has neither and is the usual choice for a path through given points.
- Cubic Bézier spans with control handles: the agent would have to work out
  handles off the path for every joint to keep it smooth, and the camera
  doesn't pass through them.
- B-splines: smooth, but they pass near the points rather than through
  them, so a via point wouldn't be a place the camera reaches.

### D4: Constant speed from an arc-length table built each frame

After evaluating the points (D1), each frame samples every span at 32 even
steps of its parameter and adds up the chord lengths into a table from
distance along the path to span and parameter. The eased progress times the
total length gives a distance, found by binary search and linear
interpolation between samples. So the camera covers equal lengths of path
in equal times however unevenly the points are spaced, and easing shapes
the speed along the whole path, not per span. With 33 spans at most that is
about a thousand cubic evaluations a frame, well under a millisecond, and
building it each frame keeps the curve on points that move.

Alternatives:
- Build the curve once at the start in world coordinates: a via point
  aboard a ship would be left where the ship was.
- The spline's own parameter: the speed changes from span to span with the
  spacing of the points, which reads as the camera surging and braking.
- Numeric integration with Newton steps: more exact than a frame needs.

### D5: The limit is 32 via points

32 via points cover a full circle round a build at ten-degree steps with
room to spare, keep the per-frame table small, and catch a script that
loops by mistake. A longer path is chained; the agent can line the joint up
by starting the next move's first span in the direction the last one ended.

### D6: The view turns by its direction, not by a blended look point

Each frame, the direction toward the old target and toward the new one are
worked out from the camera's current position and turned one into the
other by the eased progress (spherical interpolation), and the yaw and
pitch come from the result. Blending the two look points instead would
swing the view round wildly when the blended point passes near the camera.
From the player's view, the old direction is the player's view direction
at the start. Without `look`, there is no turn: the old target stays, or
from the player's view the start direction stays. Directions within a
degree of opposite turn about the vertical, so the view pans rather than
flipping over the top.

### D7: `look ahead` follows the path's tangent

`look ahead` looks along the curve's direction at the camera's place on it,
taken from the curve's shape (the derivative of the span's cubic), not from
how far the camera moved since the last frame, so it is defined even where
`ease=in` or `inout` holds the camera almost still. Over the first fifth of
the move the view turns from its old direction onto the tangent with
smoothstep, so the move doesn't open with a cut; after that it is the
tangent. At the end the camera keeps looking along the last tangent. With a
path of no length the old direction stays.

Alternatives:
- Look at the next via point: the view would jump as each point is passed.
- Look at a point a set distance ahead on the path: steadier through tight
  bends, but the camera would face across the bend rather than along its
  way, and the distance would need choosing.

### D8: Easing curves, linear by default

With `s` the progress from 0 to 1: `linear` is `s`, `in` is `s²`, `out` is
`1 − (1 − s)²`, `inout` is `s²(3 − 2s)`. The same eased value drives the
distance along the path and the turn of D6. `linear` is the default so that
a chained move keeps its speed at the joint; a single move from rest wants
`inout`, which the help suggests.

Alternatives:
- `inout` by default: nicer for one shot, but each joint of a chain would
  stop.
- Cubic curves: steeper; the quadratic ones and smoothstep are enough and
  easy to state.

### D9: Repeated optional groups in forms

`via <pos>` is an optional group declared with a repeat limit:
`Form.repeated(32, group -> group.keyword(…, "via").arg("via", POSITION))`.
The parser takes the group again while its first part fits the next token
and fails naming the limit past it; `Args` gains a way to read every value a
slot took, in order, while reading one value still gives the first. Help
shows `[via <pos>]…`, and the grammar file the group with `*`. The group
record gains its limit, with the old constructor kept at a limit of one, so
the change only adds to the extension API: `blockpilot-api` becomes 2.1.0,
and mods built against 2.0.0 work unchanged.

Alternatives:
- Nested optional groups, `[via <pos> [via <pos> …]]` to the limit: no API
  change, but the help and the grammar file would nest 32 deep and each
  point would need its own slot name.
- A path token such as `via=~1,2,3/~4,5,6`: a new token shape agents must
  learn, and still a new slot type in the API.
- A `camera via <pos>` step before the move: state carried between steps,
  which nothing else in the language does.

### D10: Ending, answering and timing out

The action counts client ticks; on the tick its duration has passed, it
replaces the move's placement with a plain one at the end point and the
target (or the last tangent for `look ahead`), as `camera at` would make,
then answers like `camera at` after the frames it waits for. It times
itself, so a move longer than 60 s doesn't time out; `stop` ends any move.

### D11: Ended early, it stays where it was last drawn

`stop` and real input cancel the move through `Action.cancel`, which
replaces the placement with a fixed one at the position and angles the last
frame recorded, so the camera doesn't jump on to the end or back. Nothing
else can end a move: a script with an action waits in the one queue of
actions until the move ahead of it ends (`Engine` steps only the queue's
head), so another session's `camera at` or `camera reset` runs after the
move, not during it. Leaving the world and turning agent control off reset
the camera, as they do now.

### D12: Entity targets are read where the entity is drawn

The target `Point` for an entity uses its position interpolated at the
frame's partial tick plus half its height, instead of its bounding box
centre, which moves once a tick and would make a smooth camera judder as it
tracks. This also smooths `camera at … look e<id>`, which the modified
"Camera placement" requirement now says.

### D13: The camera's position in status comes from the last frame

`status` adds the position field `camera`, read as `camera <x,y,z>`, from
the position `recordRendered` kept for the last frame, in absolute
coordinates, only while a camera is placed. It is the field and the point a
`screenshot` of that frame names, so the two agree, and it lets
`sample status … while camera move …` trace the path at no extra cost. The
angles stay in `cam:<yaw>/<pitch>/<roll>` as now.

### D14: A camera away from the player stays away through a move

The proposed `load-the-world-around-the-camera` change adds
`camera at … away`, ended by a `camera at` without `away`. A move is not a
new `camera at`: it and its final plain placement (D10) keep whether the
camera was away when the move began, and that change's once-a-tick
re-centring then follows the camera along the path. A move doesn't take
`away` itself; starting one from an away camera is how to fly round a
distant set. Whichever of the two changes lands second makes the final
placement keep the away record and adds a scenario for it.

## Risks / Trade-offs

- [A curve through points bulges a little outside them on a sharp turn]
  → Centripetal spacing keeps it close; another via point tightens a bend.
- [Points that move during the move change the path's length, so the speed
  along it changes with them] → It is still smooth from frame to frame, and
  a ship's speed is part of what the camera follows.
- [Starting from the player's eyes, the first frames are drawn from inside
  the player's head, now that the player model is drawn] → The head is
  mostly culled from inside; for a clean start, place the camera first with
  `camera at`.
- [The game renders fewer frames than the video's rate, so frames still
  repeat] → Each rendered frame is in the right place; `maxFps` and the
  render distance set how many there are, as for any recording.
- [`linear` stops dead at the end of a single move] → `ease=out` or
  `inout`, which the help names.
- [An extension API change, however small, has to be published] → It is
  additive, so a minor release, Blockpilot 1.1.0, ships it with
  `api_version` 2.1.0 and says so.

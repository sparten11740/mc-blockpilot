# Design

## Context

- `place` takes the first neighbour of the target cell, in
  `Direction.values()` order, that can't be replaced. It turns toward the
  centre of that neighbour's shape on the side facing the target cell
  (`Shapes.faceToward`, through `seen` for a block aboard a sub-level), then
  clicks only when the crosshair hits that block on that face. Otherwise it
  fails with `err out_of_reach` naming what the crosshair hits.
- The crosshair is the game's own pick: a ray from the eyes along the view,
  out to `blockInteractionRange()`, that stops at the first block outline,
  ignores fluids, and then looks for nearer entities.
- Which face the click hits sets how many blocks come out: a torch on the
  ground or on a wall, a log's axis, a slab's half, stairs upside down.
- `SableCommands.sees` already casts a ray from given eyes to a block, for
  `sable:tp`. It aims at the block's centre and checks the block only.

## Goals / Non-Goals

**Goals:**
- Turn today's failures into placements wherever some neighbour's face is in
  sight.
- Leave every placement that works today exactly as it is.

**Non-Goals:**
- Letting the agent choose the neighbour or face. That would be an argument
  (`against <pos>`), a grammar change worth its own proposal.
- Walking or flying to a spot from which a face is in sight.
- Clicking a replaceable block in the target cell, such as short grass,
  which today fails because the crosshair hits the grass.

## Decisions

### D1: Same order, first in sight

`place` keeps `Direction.values()` as its order of preference and takes the
first neighbour whose face is in sight. Whenever the first neighbour is in
sight, as it is in every placement that works today, the choice doesn't
change, so neither does how the block faces.

Alternatives:
- The face most squarely in view: it often differs from today's choice, so a
  torch placed on the ground today could go on a wall beside it.
- The nearest face: the same objection.

### D2: The sight test is the crosshair's ray, cast ahead

For each neighbour, `place` casts the ray the pick will cast once it has
turned: from the player's eyes through the point it would aim at, out to
`blockInteractionRange()`, stopping at the first block outline and ignoring
fluids. The face is in sight when that ray's hit is the neighbour on the face
toward the target cell. The test sits beside `seen` in `PlayerActions`, as
the code that works out where the player looks. It costs at most six rays,
once per `place`.

Entities aren't part of the test. The check after turning still catches one
in the way, as today.

Alternatives:
- Turn toward each face in turn and read the crosshair: a tick per face, and
  the player visibly hunts around.
- Test only whether the face points toward the eyes: it misses faces hidden
  behind other blocks, which is how the hole fails.

### D3: None in sight is today's path

With no neighbour in sight, `place` takes the first neighbour, as today, and
fails after turning, naming what the crosshair hits there. The error and the
existing smoke step for a side out of sight stay as they are. The error is
about the neighbour the agent would expect `place` to use.

### D4: Aboard a sub-level, unchanged

A ship can move between the sight test and the click, and its blocks are in
the plot, not where a ray through the world would meet them. For a cell
aboard a sub-level, `place` keeps the first neighbour without a sight test.

## Risks / Trade-offs

- [The ray is cast before turning, from where the eyes are now; the pick
  after turning can differ by rounding at a face seen edge-on] → The check
  after turning still decides. At worst `place` fails as it would today.
- [The test casts one ray, through the centre of the face, so a face half
  hidden at its centre counts as out of sight] → That errs toward the next
  neighbour or today's path, never toward a click the pick would miss.
- [Where today's first neighbour is hidden, the block now faces another way:
  a wall torch where the ground was hidden] → It is what a player clicking
  the face in view would get, and the reply's block id names variants such as
  `wall_torch`. It doesn't name a log's axis or a slab's half.

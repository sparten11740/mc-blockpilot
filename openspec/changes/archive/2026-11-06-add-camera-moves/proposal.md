# Proposal

## Why

A placed camera can only jump. A dolly or pan is one `camera at` per tick, so
the camera moves 20 times a second while a recording runs at 30 to 60 frames
a second, and the video shows it in steps. Such a shot also takes a step per
tick in the script, which an agent has to generate and the session has to
run one by one. Chaining straight moves doesn't help with a curved shot,
such as circling a build or following a road: the camera turns sharply at
every joint. Moving the camera smoothly along a path is what any agent
filming a build, a machine or a moving ship needs.

## What Changes

- New `camera move <pos> [via <pos> …] [look <pos|e<id>|ahead>] over
  <duration> [ease=linear|in|out|inout]`: glides the camera from where it is
  drawn now, a placed camera or the player's eyes, to the point over the
  duration. The camera is placed anew in every rendered frame, not once a
  tick, and stays placed at the end as after `camera at`. The action ends
  when the move does, and its reply is that of `camera at` for the end
  point.
- With `via <pos>`, up to 32 times, the camera passes through each via
  point in order on one smooth curve, without a corner at any of them, at a
  constant speed along the whole curve. Without `via` it goes straight.
- Without `look`, the camera keeps looking at what it looked at; from the
  player's view, it keeps the view's direction. With a point or an entity,
  the view turns toward the new target over the same time. `look ahead`
  looks the way the camera travels.
- `ease=` shapes the speed along the path: `linear` (the default) keeps it
  constant, `in` starts slowly, `out` ends slowly, `inout` does both.
- `stop` and real input end a move early, leaving the camera where the
  last frame drew it.
- `status` names a placed camera's position as drawn in the last frame,
  `camera <x,y,z>` as `screenshot` does, so `sample status … while camera
  move …` shows the path.
- Plot positions for the point, the via points or the look follow their
  sub-level, as with `camera at`, so a path can bend with a moving ship.
- An entity look target is read where the entity is drawn in each frame, so
  a camera tracking a walking mob turns smoothly, for `camera at` too.
- Forms gain repeated optional groups, such as `[via <pos>]…` with a limit.
  The extension API grows a way to declare and read them, a backward
  compatible addition that makes it `blockpilot-api` 2.1.0, shipped in
  Blockpilot 1.1.0, a minor release.

## Capabilities

### New Capabilities

None.

### Modified Capabilities
- `client-observation`: camera moves along straight or curved paths, where
  they look, their easing and ending early, and the placed camera's position
  in `status`.
- `sable-sublevels`: camera moves and their via points follow a sub-level.
- `command-language`: forms may hold repeated optional groups.
- `extension-api`: extension commands may declare repeated optional groups.

## Impact

- `game/observe/ObservationCommands.java` (the `camera` form, help and
  action) and `game/observe/CameraControl.java` (a placement whose points
  follow the move, the path maths, and the last frame's position for
  `status`); the camera mixins stay as they are, as they already place the
  camera every frame.
- `game/PerceptionCommands.java`: `camera <x,y,z>` in `status`.
- `api/Form.java` and `api/Args.java` (repeated groups and reading their
  values), `protocol/Parser.java` and `protocol/Grammar.java`; `api_version`
  and `BlockpilotApi.VERSION` 2.1.0, and `docs/extending.md`.
- `docs/grammar.ebnf` and its diagrams in `docs/grammar.xhtml`,
  `docs/commands.md`, `docs/sable.md` and the README's `camera` row.
- Unit tests for the easing curves, the path and the turn of the view, and
  for repeated groups; smoke steps for a move's length, a curved path, ending
  one early, and a recording check that a move's frames don't repeat in
  steps of a tick.
- No change to `camera at`'s reply, the error codes or `record`.

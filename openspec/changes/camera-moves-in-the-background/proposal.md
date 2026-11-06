# Proposal

## Why

A `camera move` holds its script until the move ends, and scripts with
actions wait in one queue, so nothing else can happen while the camera
moves: no server step moves a character, no `sound` plays, no other action
runs. Filming a scene where a character walks while the camera follows
meant cutting every shot into moving and still parts. A camera move only
changes what the client draws, so nothing stops it from running beside the
script.

## What Changes

- `camera move … over <duration> background` answers at once, `ok moving
  over=<n>t`, and the camera glides on its own over the duration while the
  script, and the scripts after it, go on.
- `camera wait` waits until a camera move running in the background ends
  and answers as `camera at` does there; with none running it answers at
  once with where the camera is.
- A `camera at`, `camera move` or `camera reset` replaces a move running in
  the background, starting from where the camera is drawn then.
- `stop` and real input end a move in the background as they end one in
  the foreground, leaving the camera where the last frame drew it.
- Without `background`, `camera move` answers when the move ends, as now.
- The corner scenario of curved moves samples every tick: rows two ticks
  apart lie up to half a block from the via point however the curve runs,
  so the scenario could fail by sampling alone. A row read from a frame the
  game drew late counts with the next one in the constant-speed scenario,
  and the easing scenario samples every tick and takes the next row read
  from a new frame in its place.
- A move stopped in the background is promised to stay where the last frame
  drew it, part of the way along: how far it got depends on when the `stop`
  arrives.
- The player stands still whenever a camera is placed with `away`, also on
  1.21.1, where it sank a little.
- A move from a camera placed away no longer promises its reply names the
  block at its end: the chunks there may still be on their way, and `block`
  after `settle` is what shows the world followed the camera.
- The help, `docs/commands.md`, `docs/grammar.ebnf` and its diagrams show
  the new form and keyword.

## Capabilities

### New Capabilities

None.

### Modified Capabilities
- `client-observation`: camera moves in the background and `camera wait`.

## Impact

- `game/observe/CameraMove.java`: progress from a client tick count kept by
  `CameraControl` instead of the action's own ticks, and an end that
  `CameraControl` applies for a move in the background.
- `game/observe/CameraControl.java`: the tick count, the move under way, and
  ending or freezing it.
- `game/observe/ObservationCommands.java`: the `background` keyword, the
  `wait` form, help.
- `protocol/GameBridge.java` and `protocol/Engine.java`: `stop` ends what
  runs beside the scripts; `game/BlockpilotClient.java` ticks the camera and
  counts a move under way as something real input interrupts.
- `tools/smoke_test.py`: a check that a move in the background answers at
  once, lets server steps run while it moves, ends where `camera wait` says
  and stops where `stop` leaves it.
- `docs/commands.md`, `docs/grammar.ebnf`, `docs/grammar.xhtml`, the 1.1.0
  section of `CHANGELOG.md`.

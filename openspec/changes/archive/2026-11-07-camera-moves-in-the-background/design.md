# Design

## Context

- `CameraMove` is an action: its placement works out the move's progress
  in every frame from a tick count that the action's `tick()` raises once a
  client tick, and on the tick the duration has passed it places the
  camera at the end and answers.
- Scripts with actions run one at a time from one queue, so a script
  holding a move holds every other script with an action too.
- `stop` and real input cancel the running action; a move's `cancel` fixes
  the camera where the last frame drew it.
- `camera move` is new in 1.1.0, which isn't released, so its behaviour
  can still change.

## Goals / Non-Goals

**Goals:**
- A camera move that runs beside the script, so the script and the queue
  go on while the camera moves.
- A way to wait for it when a later step needs the camera at its end.

**Non-Goals:**
- Running other actions in the background; they would compete for the
  player's inputs, where the camera competes for nothing.
- Making `background` the default: a script that films one move and then
  stops recording reads best when the move holds it.

## Decisions

### D1: The move counts the client's ticks itself

`CameraControl` keeps a count of client ticks, raised once a tick from the
client tick. A move notes the count it started at, and its progress is
`(count − start + partial tick) / duration`, so it advances whether or not
an action ticks it. The action of a move in the foreground answers on the
tick the count reaches its end, as before.

### D2: CameraControl ends a move in the background

`CameraControl` holds the move under way. On the client tick its duration
has passed, a move in the background is replaced by its end placement, the
one `camera at` would make, as the foreground action does. Placing the
camera any other way (`camera at`, another move, `camera reset`, leaving
the world, agent control off) forgets the move under way, so the newest
command wins.

### D3: `stop` and real input reach it

`Engine.stopAll`, behind `stop` and real input, asks the game to end what
runs beside the scripts through a new `GameBridge` method; the client
freezes a move under way where the last frame drew it, as `cancel` does.
Real input interrupts while a move is under way even with no action
running.

### D4: `camera wait` joins it

`camera wait` is an action that answers once no move is under way, after
the two frames `camera at` waits, with the fields `camera at` gives. With
no move under way it answers at once. It times itself, as a long move
would outlast the 60 s timeout.

## Risks / Trade-offs

- [A script ends while its move still runs] → The move goes on and ends by
  itself; `camera wait`, `camera reset` or `stop` end it earlier.
- [Two sessions move the camera] → The newest command wins, as with
  `camera at` today.

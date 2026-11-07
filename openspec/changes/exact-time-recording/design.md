# Design

## Context

- A recording reads back the frame the game finished, on the client thread,
  timed by `System.nanoTime()`; a frame the game hasn't drawn in time is
  repeated, and `repeated=` counts them.
- The client's clock (`Timer`, `DeltaTracker.Timer` on 26.x) turns real time
  into ticks to run and a partial tick for drawing; camera moves and
  interpolation read it.
- `tick freeze` and `tick step <n>` freeze and step the integrated server,
  and in singleplayer the client follows the server's tick state.
- `load client` slows the client on purpose, which a check can use.

## Goals / Non-Goals

**Goals:**
- Smooth video of any scene on any machine, frame for frame in game time.

**Non-Goals:**
- Sound in exact takes; it plays in real time and can't follow.
- Multiplayer servers, which Blockpilot can't step.
- Exactness for what doesn't run on game ticks (a mod's own wall-clock
  animation).

## Decisions

### D1: The spike first

Before anything else, a spike drives the client's clock by a fixed step per
frame while the integrated server is frozen and stepped as the clock
crosses each tick, on 1.21.1 and 26.x, and checks that ticks, partial ticks,
camera moves and input stay right. Its findings go into this design, and
the change stops there if the clock can't be driven safely.

### D2: The recorder owns the clock while it records

An exact take installs a clock step of `rate/fps` ticks per frame, `rate`
being the tick rate as the take starts, which the timer mixin takes in place
of real time, and removes it when the take ends, however it ends. Video
time is frames × the step, so marks and the summary's seconds are video
time, and the summary adds the wall time as `wall=`. The mixins live in the
version trees, `src/1.21.1` for `Timer` and `src/26.1` for
`DeltaTracker.Timer`; the pacing itself stays in `game/record`, reaching
them through one hook.

### D3: The server follows the client's clock

When an exact take starts, the server is frozen, remembering whether it was
frozen and its rate; each time the client's clock crosses a tick, the server
steps one tick, and the client waits for it before drawing the next frame,
so a frame never shows a world the server hasn't reached. The restore runs
on every ending the clock's removal runs on, `record stop`, a timed end, a
`record script`'s script ending, a failure, leaving the world, agent control
off and the game closing, so no ending leaves the world frozen.

## Risks / Trade-offs

- [Driving the client's clock touches the game loop] → The spike decides;
  the mixin is active only during an exact take.
- [Real input during a slow take] → Real input still interrupts and ends the
  take, as it ends any action, though it is read at frame boundaries, so in
  a take far slower than real time it takes longer to be noticed.
- [A take far slower than real time] → The summary's `wall=` shows what it
  cost.

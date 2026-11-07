# Proposal

## Why

A recording takes the frames the game happens to draw, on the wall clock:
when the game draws slower than the video's frame rate, frames repeat and
motion stutters, and a heavy scene, a large build, a busy machine or a
machine running something else, looks worse the more it matters. Tonight's
checks showed it too: under load, a camera move's frames arrive late and
uneven. For a showcase, the game doesn't need to run in real time at all;
it needs every video frame to show the world at exactly its moment.

## What Changes

- `record start|script|<duration> … exact` records in game time: each video
  frame shows the world at exactly its own instant, `rate/fps` ticks after
  the one before (`rate` the tick rate as the take starts, 20 unless lowered
  for slow motion), however long the game takes to draw it. The game runs at
  whatever speed drawing allows, slower or faster than real time, and the
  video plays smoothly at its frame rate with no repeated frames.
- While an exact take records, the client's clock advances by that fixed
  step per frame, and the integrated server, frozen, is stepped one tick
  each time the client's clock crosses a tick, so both sides stay in step.
- An exact take has no sound (`audio=off` is implied, and `audio=on` is
  refused), as sound plays in real time; its length and `record mark`'s
  times are in video time, `record <duration> … exact` counts video time,
  and the summary adds the wall time as `wall=`.
- However an exact take ends, the server's frozen state and tick rate are
  put back as they were.
- It needs the integrated server, as stepping does; elsewhere `exact` fails
  with `err no_server`.
- A first spike settles whether the client's clock can be driven this way
  on 1.21.1 and 26.x without breaking input, the HUD or the server's tick
  bookkeeping.

## Capabilities

### New Capabilities

None.

### Modified Capabilities
- `video-recording`: recording in game time.

## Impact

- `game/record/`: the exact pacing, the clock step per frame, and summaries
  in game time.
- Mixins on the client's timer (`Timer` on 1.21.1, `DeltaTracker.Timer` on
  26.x) to advance it by a given step while an exact take records.
- `game/ServerCommands` or the tick control: freezing and stepping the
  server in step with the client's clock, and restoring its state after.
- `tools/smoke_test.py`: a check that an exact take under an artificial
  load (`load client`, or whatever the load command is called by then, as
  a chunk-loading tool may take its name) has no repeated frames, that
  `tick query` before and after it differ by the game time its length
  covers, and that a failed take leaves the server's tick state as it was.
- `docs/recording.md`, the help, the CHANGELOG.

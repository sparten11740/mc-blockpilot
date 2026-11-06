# Design

## Context

- A recording's clock starts at its first frame: `Recording.frame()` sets
  `startNanos` when `Recorder.onFrame` first runs after `record start`, at
  the start of a frame, once the one before has been rendered. Frames and
  sound are timed from there, and video slot `k` shows the newest frame
  finished by `k/fps` (`FrameSlots`). `record status` answers `seconds` from
  the same `startNanos`, so it already gives the video time, unlabelled.
- Script steps run in client ticks, which come before the frame is rendered
  in the same pass of the game loop. A client-side change a step makes is in
  the frame rendered right after, which the next pass captures, stamped
  later than the step.
- A server command runs on the server's next tick, and its change reaches the
  client a tick or two later.
- `record` is one action command with forms per operation (`start`, `stop`,
  `status`, a duration). An action that answers on its first tick costs the
  script no tick. Scripts with actions share one queue, so a timed `record
  <duration>` holds it until it ends.
- Replies already carry rows; `record stop` has none today.
- A recording's files live in `<game dir>/blockpilot/recordings/`: the MP4,
  and while it runs the hidden `.<name>.video.mkv` and `.<name>.wav`.
  `record start` refuses a name whose MP4 exists; a failed take deletes all
  three. Closing the game stops the take and waits for the MP4.
- Besides the client thread, a recording has a writer thread, which pipes
  frames to ffmpeg behind the frame queue and can wait up to 10 s for it, a
  thread reading ffmpeg's output, and a finisher started at `stop()`. None
  of them does small file writes as they come.

## Goals / Non-Goals

**Goals:**
- Tie a named step to a time and frame in the video, exactly, without a
  calibration take.

**Non-Goals:**
- Chapters in the MP4. YouTube ignores embedded chapters and reads
  `0:00`-style timestamps from the video's description, which a script can
  make from the marks file. Desktop players would show chapters; they may
  come later, muxed from the same marks.
- Logging every step of every script during a take.
- Marks during a timed `record <duration>`: it holds the action queue, so a
  mark from another call runs after it ends and fails with `err no_capture`.
  A take with marks uses `record start` … `record stop`.
- Shifting a mark to when a server command's change reaches the client.

## Decisions

### D1: A mark is the video time when the script reaches it

`record mark` reads `System.nanoTime() - startNanos` in the step that runs
it, on the client thread, the clock `record status` and the frames use.
Before the first frame it is 0, which is where the step's effects first show.
A client-side change made in the same tick as the mark is first seen in the
slot the mark names or the one after. A server command's change shows a tick
or two later, and the help and docs say so instead of guessing a delay that
depends on the server's tick and the connection.

Alternatives:
- The time of the next captured frame: it is only known a frame later, so
  the mark couldn't answer in its own step, and slot `k` already says which
  video frame first follows the mark.
- Wall-clock time since `record start`: it counts the time to start ffmpeg
  and wait for the first frame, which the video doesn't contain.

### D2: The frame is the first slot at or after the mark

`frame=` is `ceil(t * fps)`, the first slot starting at or after the mark's
time, which `FrameSlots` already computes for the slots before a time. It is
exact where `seconds`, rounded to 0.01 s as every reply's numbers are, is
within half a frame only up to 100 fps. Editing tools seek by either.

### D3: Marks live on the recording and come back with its end

`Recording` keeps a list of marks (label, nanoseconds), added to on the
client thread, where `record mark` runs, and copied into the summary when
`stop()` runs there too. The reply of `Stop`, which `record stop` and the
timed form share, turns them into rows keyed by label, so marks made in
separate calls come back with `record stop`; a timed take never has any. A
recording the game closes or the encoder fails has no reply to carry them;
its marks are lost with it, as the mark replies already gave their times.

The cap of 1,000, as `sample` has, keeps the stop reply bounded; past it
`record mark` fails with `err too_large` and the take goes on.

### D4: `mark` is another form of `record`

`record mark <label>` is a form beside `start`, `stop` and `status`,
answering on its first tick like `status`. The label is a word, so it can't
hold spaces; repeated labels are kept as separate marks.

Alternatives:
- A query, so it would run beside a timed recording: queries are read-only
  and may run in `wait` and `sample`, which would mark on every check.
- A top-level `mark` command: a mark only means something within a
  recording, and `record status` already lives there.

### D5: The marks file is rewritten whole, on the client thread

Each mark rewrites `<name>.marks.json` with every mark so far: written to a
hidden `.<name>.marks.json.tmp` beside it, then moved over it atomically,
so a reader or a crash finds the old file or the new one, never half of
one. Writing at each mark, not at the end, keeps the marks when the game
closes or crashes before `record stop`. The file is small, under 100 KB at
the cap, and written in the step that makes the mark, on the client thread,
so it is complete when `record mark` answers and when `record stop` does.
`seconds` is rounded as in the reply and `frame` is exact, so the file and
the replies agree.

`record start` also refuses a name whose marks file exists: a marks file
left from a deleted video would otherwise be taken for the new take's, or
be overwritten. A failed take deletes its marks file with its other files.

Alternatives:
- Write the file once, at `record stop`: a take the game closes or crashes
  loses its marks, which is when they are hardest to recover.
- Append a line per mark (JSON Lines): no rewrite, but a crash can leave a
  half line, and editors and scripts read one JSON object more easily.
- Write it on a thread of its own: the writes would have to stay in order
  and `record stop` would wait for the last one, for a write that takes
  well under a millisecond. The writer thread is behind the frame queue,
  which can hold a mark's write back by seconds.

## Risks / Trade-offs

- [An agent expects the mark to line up with what a server command does] →
  The help, `docs/recording.md` and the reply's frame say what the mark
  times; a mark after the change, read by `wait … until`, times the moment
  the client saw it.
- [Marks in a long take with a mark per tick reach the cap] → 1,000 marks is
  50 s at one per tick; past it the error says so and the recording keeps
  going.
- [The marks file can't be written, say on a full disk] → The mark is still
  kept and answered, the failure is logged, and the next mark writes the
  whole file again. A full disk fails the encoder soon after, which ends the
  take and removes its files.
- [Rewriting the file at a mark per tick near the cap] → Under 100 KB per
  write, on the client thread; a take that marks every tick is the case the
  cap bounds.

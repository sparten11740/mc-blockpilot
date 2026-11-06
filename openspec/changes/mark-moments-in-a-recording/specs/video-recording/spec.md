## ADDED Requirements

### Requirement: Marks in a recording
`record mark <label>` SHALL note the video time at which the script reached
it: the time from the video's first frame, the clock `record status` gives as
`seconds`, and 0 before the first frame. It SHALL answer `ok mark <label>
<seconds>s frame=<k>`, `k` being the first video frame at or after that time,
without costing the script a tick.

#### Scenario: A change right after a mark
- **WHEN** an agent runs `option hud off; record start take fps=20
  audio=off; wait 10t; record mark hud; option hud on; wait 10t; record stop`
- **THEN** the mark's reply names a frame `k` about 10 ticks in, the frames
  before `k` show no hotbar, and frame `k + 1` and those after show it

#### Scenario: Before the first frame
- **WHEN** an agent runs `record start take; record mark first` in one
  script, so the mark comes before the game has rendered a frame for the take
- **THEN** the mark's reply is `ok mark first 0s frame=0`

### Requirement: A mark needs a running recording
With no recording running, or one finishing, `record mark` SHALL fail with
`err no_capture`; after an encoder failure, with `err record_failed`, as
`record status` does. A recording SHALL take at most 1,000 marks; one more
SHALL fail with `err too_large` and leave the recording running.

#### Scenario: No recording running
- **WHEN** no recording runs and an agent runs `record mark start`
- **THEN** the result is `err no_capture` saying no recording is running

#### Scenario: Too many marks
- **WHEN** a recording has 1,000 marks and an agent runs `record mark more`
- **THEN** the result is `err too_large` naming the limit, and `record
  status` still shows the recording running

### Requirement: Marks in the reply that ends a recording
The reply of `record stop` SHALL list the recording's marks as rows in the
order they were made, each with its label, video time and frame, as `record
mark` answered. A recording without marks SHALL have no rows. A timed `record
<duration>` holds the action queue until it ends, so its take has no marks.

#### Scenario: Two marks
- **WHEN** an agent runs `record start take; wait 20t; record mark open;
  wait 20t; record mark close; record stop`
- **THEN** the stop reply has the rows `open` at about 1 s and `close` at
  about 2 s, in that order, with the times and frames their marks answered

#### Scenario: Marks from separate calls
- **WHEN** an agent runs `record start take`, then `record mark open` and
  `record stop` as separate calls
- **THEN** the stop reply lists the mark `open`

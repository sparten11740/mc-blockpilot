## ADDED Requirements

### Requirement: Recording in game time
With `exact`, `record start`, `record script` and `record <duration>` SHALL
record in game time: frame `k` showing the world `k` steps of `rate/fps`
ticks after the start, `rate` being the tick rate as the take starts,
however long the game takes to draw it, with no repeated frames and the
integrated server stepped in step. The summary SHALL add the wall time as
`wall=<seconds>s`. Without an integrated server, `exact` SHALL fail with `err
no_server`.

#### Scenario: Smooth under load
- **WHEN** an agent runs `tick query; load client 40; record 2s smooth fps=30
  exact; load off; tick query` while a camera move runs in the background
- **THEN** the take has 60 frames, none repeated, a length of 2 s, and the
  two `tick query` replies differ by about 40 ticks

### Requirement: An exact take has no sound
An exact take SHALL record without sound, as sound plays in real time, and
`audio=on` with `exact` SHALL fail with `err syntax` saying so.

#### Scenario: Sound refused
- **WHEN** an agent runs `record start take exact audio=on`
- **THEN** the result is `err syntax` saying an exact take has no sound

### Requirement: The server after an exact take
However an exact take ends, by `record stop`, its timed end, the end of a
`record script`'s script, a failure, leaving the world, agent control off
or the game closing, the integrated server's frozen state and tick rate
SHALL be as they were before the take.

#### Scenario: A failure mid-take
- **WHEN** an exact take's encoder fails while the server, unfrozen before
  the take, is stepped by it
- **THEN** `tick query` afterwards answers `frozen=false` at the rate it had

## MODIFIED Requirements

### Requirement: Timed recording
`record <duration> [name]`, with the options `record start` takes, SHALL
start a recording, end it after that long on the wall clock, or in video
time with `exact`, and answer as `record stop` does. Cancelling the wait
with `stop` SHALL leave the recording running until `record stop`.

#### Scenario: Ten seconds in one line
- **WHEN** an agent runs `use ~0,0,2; record 10s gears`
- **THEN** the reply comes after about 10 s with the file `gears.mp4` and a
  length of about 10 s

### Requirement: Frame rate and timing
`fps=<n>`, from 1 to 120, SHALL set the frame rate, by default 60. Unless
`exact`, frame `k` SHALL show the newest frame the game rendered by `k/fps`
seconds after the start, repeating it when no newer one was rendered, so
the video lasts as long as the recording did on the wall clock.

#### Scenario: Game hitches
- **WHEN** the game stops rendering for half a second during a recording at
  `fps=60`
- **THEN** the video holds the last frame for half a second, about 30 frames,
  and its length still matches the wall clock

### Requirement: Slow motion by the tick rate
With the tick rate lowered by `tick rate`, a recording SHALL show the game
slowed by the same factor, a new frame drawn between ticks: entities, particles,
camera moves and `wait` all go by the game's ticks, while, unless `exact`, the
length of a timed recording and the times `record mark` gives go by the wall
clock. An exact take steps `rate/fps` ticks a frame, so it shows the same slow
motion with every frame exact, and its marks are in video time.

#### Scenario: A quarter of the speed
- **WHEN** an agent runs `tick rate 5; tick query; record 2s slow fps=20
  audio=off; tick query; tick rate 20`
- **THEN** the game time advances by about a quarter of the 2 s at 20 ticks
  a second while the take lasts, and the take has about 40 frames, few of
  them repeated

#### Scenario: A camera move at a quarter of the speed
- **WHEN** an agent runs `tick rate 5; camera at ~0,2,0; camera move
  ~4,2,0 over 20t; tick rate 20`
- **THEN** the move takes about 4 s, where at 20 ticks a second it takes 1 s

#### Scenario: A mark after a wait at a quarter of the speed
- **WHEN** an agent runs `tick rate 5; record start slow fps=20 audio=off;
  wait 20t; record mark after; record stop; tick rate 20`
- **THEN** the mark's time is about 4 s into the video, as `wait 20t` took
  4 s on the wall clock

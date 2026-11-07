## ADDED Requirements

### Requirement: Slow motion by the tick rate
With the tick rate lowered by `tick rate`, a recording SHALL show the game
slowed by the same factor, a new frame drawn between ticks: entities,
particles, camera moves and `wait` all go by the game's ticks, while the
length of a timed recording and the times `record mark` gives go by the
wall clock.

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

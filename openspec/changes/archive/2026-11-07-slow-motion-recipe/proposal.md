# Proposal

## Why

Slow motion is the usual way to show something fast, an explosion, a
machine's cycle, a mob's leap, and Blockpilot can already film it: lower
the game's tick rate and record at a normal frame rate, and the game draws
new frames between its ticks. Nothing says so, though, nor what slows with
the game and what doesn't, so an agent either doesn't think of it or is
surprised that a camera move takes four times as long while `record 8s`
still lasts eight seconds.

## What Changes

- `docs/recording.md` gains a slow-motion recipe: `tick rate 5; record 8s
  blast fps=60 audio=off; tick rate 20`, what slows with the tick rate
  (entities, particles, camera moves, `wait`), what goes by the wall clock
  (the length of a timed recording, `record mark`), and that in practice
  sounds play at their normal speed, so they belong in the edit.
- The video-recording spec states that behaviour, and a smoke check holds
  it.
- The 1.1.0 section of `CHANGELOG.md` mentions the recipe.

## Capabilities

### New Capabilities

None.

### Modified Capabilities
- `video-recording`: slow motion by the tick rate.

## Impact

- `docs/recording.md`, `CHANGELOG.md`.
- `tools/smoke_test.py`: a check that a take at a quarter of the tick rate
  advances the game by a quarter as much, that a camera move takes four
  times as long, and that a mark after `wait 20t` lands about 4 s in.

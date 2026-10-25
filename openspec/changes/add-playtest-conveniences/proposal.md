# Proposal

## Why

Agents play-testing mods keep working around five gaps. Restarting the game
means killing a process by PID. Touch surfaces inside a block need hand-computed
points. Waiting for a specific chat line isn't possible. A screenshot taken
right after a click can miss the click's effect. A climb can't be traced in
short rows. Each costs extra calls and guesswork in every test loop.

## What Changes

- `quit` saves the world, leaves it and closes the game. The CLI waits until
  the game process is gone, so restart loops no longer kill processes.
- `face` and `use` take `at <u,v,w>`: a point inside the target block in the
  block's own frame, turned by its facing, for touch surfaces such as monitors
  and control panels. `status` prints where the crosshair meets the block in
  that frame (`hit=u,v,w`), so a point can be read off instead of computed.
  Both work for blocks aboard a sub-level too.
- `messages` takes `match=<pattern>`, a case-insensitive pattern over the
  text, so `wait messages … until count>0` can wait for a specific line.
- `screenshot` captures the next rendered frame, as its requirement already
  says; it took the frame rendered before it started. A screenshot right after
  a `click` then shows what the click did in the following tick.
- With Sable: `sable:info <sub-level> brief` gives a short flight readout
  (heading, pitch, roll, speed, climb rate, height above ground), sized for
  `sample … every Nt` over a take-off or climb. The full `sable:info` gains the
  height above ground too.

## Capabilities

### New Capabilities

None.

### Modified Capabilities
- `game-session`: `quit` closes the game cleanly.
- `player-control`: `face` and `use` aim at a point within a block.
- `game-perception`: `status` reports the crosshair's hit point within the
  block; `screenshot` captures a frame rendered after it started.
- `client-observation`: the message log takes a text pattern.
- `dev-integration` (from `add-test-harness`): the CLI waits for the game to
  exit after `quit`.
- `sable-sublevels` (from `add-sable-support`): a brief flight readout and the
  height above ground.

## Impact

- **Code:** a `quit` session command; block-frame points in the aiming code
  (`ActionCommands`) and in the crosshair report (`PerceptionCommands`); the
  message log filter; next-frame capture in `Screenshots` and the screenshot
  action; the flight readout in the `sable:` commands; the CLI's exit wait.
- **Protocol:** new options and fields only; existing replies keep their form,
  apart from the extra `hit=` in `status` while aiming at a block.
- **Docs:** README command table and the CLI section.
- **Testing:** unit tests for the block-frame transform and the text pattern;
  in-game checks on NeoForge and Fabric, with Sable for the sub-level cases.

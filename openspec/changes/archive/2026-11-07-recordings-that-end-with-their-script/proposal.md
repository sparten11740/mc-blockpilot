# Proposal

## Why

A take usually covers one script: set the shot up, start recording, run
the action, stop. When a step in between fails, or `stop` or real input
cancels the script, the `record stop` at its end never runs: the recording
goes on, filming nothing in particular, the next take's `record start`
fails with `err busy`, and the agent has to notice and stop it by hand.
And a script that should simply film itself to its end has to remember the
`record stop`, whose reply then sits among the action's.

## What Changes

- `record script [name] [width=] [fps=] [hud=off] [audio=off]` starts a
  recording as `record start` does, and the recording ends with the script:
  after its last step, at an error that stops it, or when `stop` or real
  input cancels it. The script's reply comes once the file is saved, with
  the take's summary, the fields `record stop` gives, as the event `!
  record …`. A `record stop` in the script ends it earlier, as for any
  recording.
- A command can ask for an action to run when its script ends, however it ends,
  before the reply: `CommandContext.atEnd(Action)` in `blockpilot-api`, an
  addition. The engine runs such endings on the client thread, each answering as
  an event in the script's reply, for at most 60 s each unless the ending times
  itself, as Blockpilot's own can.
- The `record` help, `docs/recording.md`, `docs/extending.md`, the
  grammar files and the 1.1.0 section of `CHANGELOG.md` describe both.

## Capabilities

### New Capabilities

None.

### Modified Capabilities
- `video-recording`: a recording that ends with its script.
- `extension-api`: an action run when the script ends.

## Impact

- `api/CommandContext.java`: `atEnd`, a default that refuses where there is
  no script; `BlockpilotApi.VERSION` stays 2.1.0, which this release
  already brings.
- `protocol/ScriptJob.java`: the endings, run after the last statement or
  after a cancel; `protocol/Engine.java`: a cancelled script with endings
  is stepped until they have answered.
- `game/record/RecordCommands.java`: the `script` form and its ending.
- Tests: engine tests for endings after the last step, after an error and
  after `stop`, the event they leave, the 60 s limit, and a context without
  a script; a smoke check of `record script` ending at a failing step.
- `docs/recording.md`, `docs/extending.md`, `docs/grammar.ebnf`,
  `docs/grammar.xhtml`, `CHANGELOG.md`.

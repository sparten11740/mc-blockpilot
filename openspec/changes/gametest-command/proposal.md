# Proposal

## Why

Mod authors already write GameTests, Minecraft's own in-world tests, which
Blockpilot can neither run nor report on. An agent testing a mod therefore
has two kinds of tests that don't meet: its Blockpilot scripts, and the
mod's GameTests, which it can only run by hand from the game's chat and
read from the log.

## What Changes

- `gametest run <selector>` runs the registered GameTests whose ids match
  the selector, such as `mymod:*`, on the integrated server, and answers
  with a row per test, passed or failed with its message, on all 11
  targets: the 9 of 26.x and the 2 of 1.21.1.
- A selector that matches nothing fails with `err no_test`; NeoForge 1.21.1
  without GameTests enabled fails with `err unsupported` saying what
  enables them.
- The smoke check runs tests Blockpilot brings itself, with no test mod: a
  data pack on 26.x, and on 1.21.1 two tests in a source set only the dev
  runs include.
- The help, `docs/commands.md`, the README and the CHANGELOG describe it.

## Capabilities

### New Capabilities

None.

### Modified Capabilities
- `server-bridge`: running GameTests.

## Impact

- `game/GameTests.java`, new, and one adapter each for 1.21.1 and 26.x over
  that version's public GameTest classes, with no mixin.
- A 26.x data pack with two block-based tests for the smoke check, and for
  1.21.1 a dev-only source set with two tests, kept out of the published
  jars.
- `err no_test` in the error table; `tools/smoke_test.py`, docs as above.

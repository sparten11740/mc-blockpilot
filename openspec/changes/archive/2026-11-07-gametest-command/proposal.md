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
- A selector that matches nothing fails with `err no_test`; NeoForge, which
  runs GameTests only in development with them enabled, fails without that
  with `err unsupported` saying what enables them; a client without an
  integrated server fails with `err no_server`, as other server commands do.
- The smoke check runs tests Blockpilot brings itself, with no test mod: a
  data pack on 26.x, and on 1.21.1 two tests in a source set only the dev
  runs include.
- When the game asks before loading a world whether to back it up as it uses
  experimental settings, as a data pack's GameTests make it, `join world`,
  and `snapshot save` and `restore`, which join the world again, load a test
  world without a backup, saying so with `experimental=true`, and fail at
  once with `err join_failed` for any other world, where they waited until
  they timed out.
- The help, `docs/commands.md`, the README and the CHANGELOG describe it.

## Capabilities

### New Capabilities

None.

### Modified Capabilities
- `server-bridge`: running GameTests.
- `game-session`: worlds using experimental settings.

## Impact

- `game/GameTests.java`, new, and one adapter each for 1.21.1 and 26.x over
  that version's public GameTest classes, with no mixin.
- A 26.x data pack with two block-based tests for the smoke check, and for
  1.21.1 a dev-only source set with two tests, kept out of the published
  jars.
- `game/common/ExperimentalWorlds.java`, new, with an accessor mixin for the
  backup question's listener, used by `join world` and `snapshot`.
- `err no_test` in the error table; `tools/smoke_test.py`, docs as above.

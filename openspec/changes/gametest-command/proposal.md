# Proposal

## Why

Mod authors already write GameTests, Minecraft's own in-world tests, which
Blockpilot can neither run nor report on. An agent testing a mod therefore
has two kinds of tests that don't meet: its Blockpilot scripts, and the
mod's GameTests, which it can only run by hand from the game's chat and
read from the log.

## What Changes

- `gametest run <test|namespace:*>` runs the selected GameTests on the
  integrated server, where the game or its loader has them enabled, and
  answers with a row per test, passed or failed with its message.
- Where GameTests aren't enabled, it fails with `err unsupported` saying
  what enables them on that loader.
- Which versions it covers is settled by a first spike: where each version
  and loader reports a test's result and message, and whether that is
  reachable without a mixin; versions without such a hook answer `err
  unsupported`.
- The help, `docs/commands.md`, the README and the CHANGELOG describe it.

## Capabilities

### New Capabilities

None.

### Modified Capabilities
- `server-bridge`: running GameTests.

## Impact

- `game/GameTests.java`, new, and one adapter per version family over that
  version's GameTest runner.
- A dev-only GameTest fixture, one per loader that can register tests, in a
  source set kept out of the published jars.
- `tools/smoke_test.py`, docs as above.

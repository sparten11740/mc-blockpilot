# Proposal

## Why

Key combinations take three commands: `key down f3; key press l; key up f3` to
start the vanilla profiler, and the same pattern for every other F3 combo and
for Ctrl or Shift shortcuts in a screen. `key f3+l` is refused as an unknown
key. A chord in one command is shorter, and it can't leave a modifier held if
a later step fails.

## What Changes

- In `key`, `+` joins keys into a chord: `key f3+l`, `key press left_control+a
  2t`. A press holds the earlier keys in order, presses the last for the
  given ticks, then releases them all in reverse order. `key down` holds every
  key of the chord in order, and `key up` releases them in reverse.
- Each part is a key name or key-mapping id, as a single key is now. A chord
  with an empty part, such as `f3+`, fails with `err syntax`.

## Capabilities

### New Capabilities

None.

### Modified Capabilities
- `player-control`: key chords in `key`.

## Impact

- `game/InputCommands.java` (`key`), `game/input/KeyNames.java` (splitting a
  chord), with tests in `KeyNamesTest`.
- README: the input row.

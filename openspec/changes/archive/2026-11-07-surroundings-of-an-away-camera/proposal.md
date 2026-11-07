# Proposal

## Why

With a camera placed `away`, the client holds the world around the camera,
and once the camera is farther from the player than the view distance the
server sends nothing around the player at all. Yet `look` and `find` search
around the player, so while an agent films a set a thousand blocks off they
come back empty: it can't see the actors it summoned there, the blocks of
the set or what to aim at, and has to guess positions or read them one
`block` at a time.

## What Changes

- While a camera is placed with `away`, `look` and `find` search around the
  camera's place, where the server takes the player to be, the same point
  `@<name>` measures from, and sort what they find by distance from there.
- Their rows still give positions relative to the player's block, with the
  absolute ones beside them, and `~` in their arguments stays relative to
  the player, so nothing a script already reads changes meaning.
- With an away camera placed, `look` and `find` no longer see around the
  player, even when the camera is within the view distance; `camera reset`
  brings them back.
- The help of `look` and `find` and `docs/commands.md` say so.

## Capabilities

### New Capabilities

None.

### Modified Capabilities
- `game-perception`: `look` and `find` around an away camera.

## Impact

- `game/observe/AwayCamera.java`: the point the client measures from, the
  away camera's place or the player, shared by `look`, `find` and the
  `@<name>` lookup, which works it out inline today.
- `game/PerceptionCommands.java`: `look` and `find` centre on it.
- `game/NamedEntities.java`: uses the shared point.
- `tools/smoke_test.py`: a check that `look` and `find` see an armor stand
  on a set by an away camera.
- `docs/commands.md`, the 1.1.0 section of `CHANGELOG.md`.

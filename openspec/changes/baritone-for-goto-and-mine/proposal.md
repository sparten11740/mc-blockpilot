# Proposal

## Why

Blockpilot's own pathfinder walks: it steps up a block, drops, swims and
climbs ladders, but never digs, bridges, pillars or parkours, and it plans
only through loaded chunks. A trip across rough terrain or into a cave ends
in `err no_path` or `err stuck`, and `mine` by type walks only to blocks it
can reach that way. Baritone, a widely used pathfinding mod, does all of
that, and many players who would drive the game through Blockpilot already
have it installed. Using it when it is there, without depending on it,
gives `goto` and `mine` the reach of a dedicated pathfinder.

## What Changes

- With Baritone installed, `goto <pos|e<id>> [near=]` takes
  `via=baritone`, and `mine <block|#tag> x<n>` takes it too: Blockpilot
  hands the goal to Baritone, reports its progress as the action's, and
  answers as the built-in pathfinder does, `ok` with `took=`, `err stuck`
  or `err no_path`. Without Baritone, `via=baritone` fails with `err
  unsupported`, the code for what a loader or machine lacks, naming
  Baritone, and the help of `via=` says so.
- `via=blockpilot`, the default, keeps the built-in pathfinder, which never
  breaks or places blocks; Baritone does both, so it is for worlds the agent
  may change, and a config key `"pathfinder"` makes it the default for
  players who want that.
- `stop`, real input and a script's failure end Baritone's goal as they end
  the built-in walk, and Blockpilot releases the inputs Baritone held.
- Baritone's code is not copied or bundled: Blockpilot calls its public
  API, looked up at run time, so Blockpilot stays Apache-2.0 and Baritone a
  mod the player installs.
- The help, `docs/commands.md`, the README and the CHANGELOG describe the
  option.

## Capabilities

### New Capabilities

None.

### Modified Capabilities
- `player-control`: `goto` and `mine` through Baritone when installed.

## Impact

- `game/path/`: a `Pathfinder` seam, the built-in `Navigator` behind it and
  a `BaritonePathfinder` that reaches Baritone's API reflectively.
- `game/MovementCommands.java`, `game/InteractCommands.java`: the `via=`
  option and the backend choice.
- `common/Config`: the `pathfinder` default.
- Dev runs: Baritone's release jar for 1.21.1 Fabric and NeoForge as a
  run-time-only mod of a dedicated run configuration, downloaded like the
  API fixture, so the smoke check can exercise it; it skips where no
  Baritone build exists (26.x today).
- `tools/smoke_test.py`: a `goto via=baritone` check across a gap the
  built-in pathfinder can't cross, skipped without Baritone.

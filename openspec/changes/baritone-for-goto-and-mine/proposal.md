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

- With Baritone installed, `goto <pos|e<id>> [near=]` and the walk of `mine
  <block|#tag> x<n>` into reach of each block go through it on their own, with
  no option to give: Blockpilot hands the goal to Baritone, reports its progress
  as the action's, and answers as the built-in pathfinder does, `ok` with
  `took=`, `err stuck` or `err no_path`, adding `by=baritone` so the agent sees
  which pathfinder walked. Without Baritone, nothing changes.
- Baritone breaks and places blocks where its own settings allow it
  (`allowBreak`, `allowPlace`), which the player sets in Baritone; the
  built-in pathfinder never does.
- `stop`, real input and a script's failure end Baritone's goal as they end
  the built-in walk, and Blockpilot releases the inputs Baritone held.
- Baritone's code is not copied or bundled: Blockpilot calls its public
  API, looked up at run time, so Blockpilot stays Apache-2.0 and Baritone a
  mod the player installs. Builds exist for every NeoForge and Fabric
  target, 1.21.1, 26.1, 26.2 and 26.3; the vanilla agent targets have no
  loader to load it, so they keep the built-in pathfinder.
- The help of `goto` and `mine`, `docs/commands.md`, the README and the
  CHANGELOG say so.

## Capabilities

### New Capabilities

None.

### Modified Capabilities
- `player-control`: `goto` and `mine` through Baritone when installed.

## Impact

- `game/path/`: a `Pathfinder` adapter port for walking, with
  `BuiltInPathfinder` over `Navigator` and `BaritonePathfinder` reaching
  Baritone's API reflectively as its adapters, and `Pathfinders` choosing
  the first available one, so another pathfinding mod is one more adapter.
- `game/MovementCommands.java`, `game/InteractCommands.java`: the walk and
  the mining through whichever pathfinder is available.
- Dev runs: the Baritone build for each NeoForge and Fabric target, from
  cabaletta's GitHub releases and checked against their checksums, as a
  run-time-only mod of a dedicated run configuration, downloaded like the
  API fixture, so the smoke check can exercise it.
- `tools/smoke_test.py`: a check, run in the Baritone run configuration
  and skipped elsewhere, that `goto` digs into a closed room the built-in
  pathfinder can't reach and that `mine` by type reaches blocks behind a
  wall through Baritone.

# Proposal

## Why

`perf` counts the block changes the integrated server sends to clients per
block, block entity and sub-level, but not per position. When one kind of
block churns, the summary names the block but not which ones: of 33 identical
thrusters on a ship, or a hundred observers in a redstone build, a test can't
tell which are changing state every tick without probing each by hand.

## What Changes

- A capture also counts those block changes per position, and the summary
  gets `block_change_positions`: the 50 busiest positions, ranked by changes
  per second, each with its absolute position, the block it last changed to,
  its sub-level, its dimension outside the overworld, the count and the rate.
  A block aboard a sub-level is given at its position in the plot.
- Position tracking is bounded at 65,536 positions per capture. Changes at
  positions beyond that are counted as `block_change_positions_untracked`,
  which the summary gives when there are any.
- The text overview of `perf stop` shows the top five positions as `change
  at` rows, as it shows the top five of every list.
- No `top=` option: the saved file holds the 50, the overview the top five.

## Capabilities

### New Capabilities

None.

### Modified Capabilities
- `performance-capture`: block changes counted and ranked per position.

## Impact

- `game/perf/Perf.java` (counting, summary), `game/perf/BlockChanges.java`
  (passing the dimension and position from the server hook),
  `game/perf/PerfCommands.java` (overview rows, help).
- `PerfTest`: ranking by position and the bound.
- README: the performance row of the command table.

## ADDED Requirements

### Requirement: Block changes by position
When an integrated server runs, the summary SHALL also count per position the
block changes it counts per block, and list the 50 busiest positions as
`block_change_positions`, ranked by changes per second, highest first. Each
entry SHALL give the absolute position, in the plot for a block aboard a
sub-level, the block it last changed to, the sub-level's name, the dimension
when it isn't the overworld, the count and the rate.

#### Scenario: Which thrusters churn
- **WHEN** a capture runs while three of a ship's 33 thrusters change their
  block state every tick and the others stay still
- **THEN** `block_change_positions` lists those three plot positions first,
  each with the thruster's block id, the ship's name and about 20 changes per
  second

#### Scenario: Text overview
- **WHEN** an agent runs `perf stop` in text mode after such a capture
- **THEN** the reply has a `change at <x,y,z> <block> on <sub> <rate>/s` row
  for each of the five busiest positions

### Requirement: Bounded position tracking
A capture SHALL track at most 65,536 positions. Changes at positions beyond
that SHALL count only towards `block_change_positions_untracked`, which the
summary SHALL give when it is above zero. The counts by block SHALL still
include them.

#### Scenario: Large terrain update
- **WHEN** blocks at more than 65,536 positions change during one capture
- **THEN** `block_change_positions` ranks the positions tracked before the
  bound was reached, and `block_change_positions_untracked` counts the
  changes at the others

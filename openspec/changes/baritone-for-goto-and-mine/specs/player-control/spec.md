## ADDED Requirements

### Requirement: Pathfinding through Baritone
With Baritone installed, `goto`, and the walk of `mine` by type into reach of
each block, SHALL go through Baritone on their own, with no option, adding
`by=baritone` to their replies; `stop` and real input SHALL end its goal.
Without Baritone, they SHALL use the built-in pathfinder as before.

#### Scenario: Into a closed room
- **WHEN** an agent with Baritone installed runs `goto <a cell inside a
  closed stone room>`
- **THEN** Baritone digs in and the result is `ok` with `by=baritone`,
  where without Baritone it is `err no_path`

#### Scenario: Mining by type
- **WHEN** an agent with Baritone installed runs `mine gold_block x3` with
  three gold blocks behind a stone wall the built-in walk can't pass
- **THEN** it reaches each through Baritone, digs it, and the result is `ok
  mine 3/3 gold_block` with `by=baritone`

#### Scenario: Stopped
- **WHEN** session B runs `stop` while session A's `goto` walks through
  Baritone
- **THEN** A answers `err cancelled` and the player stops moving within a
  few ticks

### Requirement: What a walk through Baritone answers
A walk through Baritone SHALL answer as the built-in pathfinder does: `ok`
near the target, `err no_path` when Baritone's path calculation fails, and
`err stuck <pos>` when its events report the path it followed as failed or
abandoned other than by Blockpilot's own cancel.

#### Scenario: No way there
- **WHEN** an agent with Baritone installed runs `goto` to a cell enclosed
  in bedrock
- **THEN** the result is `err no_path` with `by=baritone`

## MODIFIED Requirements

### Requirement: Pathfinding movement
`goto <pos|e<id>> [near=<r>]` without Baritone installed SHALL compute a
path through loaded chunks and walk it. The path may walk, sprint, jump one
block up, drop up to three blocks, swim, and climb ladders/vines. It SHALL
NOT break or place blocks. It fails with `err no_path`, or with `err stuck
<pos>` if progress stops for 3 s. Its jumps SHALL NOT turn creative flight
on or off.

#### Scenario: Stairs in creative
- **WHEN** an agent in creative, not flying, without Baritone installed,
  runs `goto` to the top of a flight of stairs
- **THEN** the player walks up the stairs and arrives without flying

#### Scenario: Unreachable target
- **WHEN** the target is enclosed by solid blocks and Baritone isn't
  installed
- **THEN** the result is `err no_path` without the player moving

#### Scenario: Follow entity
- **WHEN** an agent without Baritone installed runs `goto e812 near=2`
- **THEN** the player walks until within 2 blocks of the entity's current
  position

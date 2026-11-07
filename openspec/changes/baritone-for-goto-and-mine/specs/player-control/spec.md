## ADDED Requirements

### Requirement: Pathfinding through Baritone
With Baritone installed, `goto` and `mine` by type SHALL take `via=baritone` and
hand their goal to Baritone: `ok` near the target, `err no_path` when its path
calculation fails, `err stuck <pos>` when its events report the path it followed
as failed or abandoned other than by Blockpilot's cancel. `stop` and real input
SHALL end its goal. Without Baritone, `via=baritone` SHALL fail with `err
unsupported` naming Baritone.

#### Scenario: Into a closed room
- **WHEN** an agent with Baritone installed runs `goto <a cell inside a
  closed stone room> via=baritone`
- **THEN** Baritone digs in and the result is `ok`, where `goto` without
  `via=` answers `err no_path`

#### Scenario: Mining by type
- **WHEN** an agent with Baritone installed runs `mine stone x3
  via=baritone` with stone nearby
- **THEN** Baritone mines three stone blocks and the result is `ok mine
  3/3 stone`, as the built-in `mine` reports it

#### Scenario: Without Baritone
- **WHEN** an agent without Baritone runs `goto 10,64,10 via=baritone`
- **THEN** the result is `err unsupported` naming Baritone

#### Scenario: Stopped
- **WHEN** session B runs `stop` while session A's `goto … via=baritone`
  walks
- **THEN** A answers `err cancelled` and the player stops moving within a
  few ticks

### Requirement: Choosing the pathfinder
`via=` SHALL name a registered pathfinder, `blockpilot` (built in) or
`baritone`; another name SHALL fail with `err syntax` listing them. Without
`via=`, `goto` and `mine` SHALL use the config's `pathfinder`, `blockpilot`
unless set.

#### Scenario: The config's default
- **WHEN** the config sets `"pathfinder": "baritone"` and an agent with
  Baritone installed runs `goto <a cell inside a closed stone room>`
- **THEN** Baritone digs in, and `goto … via=blockpilot` to the same cell
  answers `err no_path`

## MODIFIED Requirements

### Requirement: Pathfinding movement
`goto <pos|e<id>> [near=<r>]` with the built-in pathfinder SHALL compute a
path through loaded chunks and walk it. The path may walk, sprint, jump one
block up, drop up to three blocks, swim, and climb ladders/vines. It SHALL
NOT break or place blocks. It fails with `err no_path`, or with `err stuck
<pos>` if progress stops for 3 s. Its jumps SHALL NOT turn creative flight
on or off.

#### Scenario: Stairs in creative
- **WHEN** an agent in creative, not flying, runs `goto` to the top of a flight
  of stairs
- **THEN** the player walks up the stairs and arrives without flying

#### Scenario: Unreachable target
- **WHEN** the target is enclosed by solid blocks
- **THEN** the result is `err no_path` without the player moving

#### Scenario: Follow entity
- **WHEN** an agent runs `goto e812 near=2`
- **THEN** the player walks until within 2 blocks of the entity's current
  position

## ADDED Requirements

### Requirement: Block state filters
Where a command takes a block as an id, `#tag` or `*pattern` (`find`, `mine`
by block, `sable:find`, `sable:tp`), it SHALL also take one with states in
brackets, `<block>[<state>=<value>,...]`, as Minecraft writes them. It then
SHALL match only the blocks whose state holds each value. A tag or pattern
with states SHALL match only the blocks that have each state with that value.

#### Scenario: Logs by axis
- **WHEN** an agent runs `find oak_log[axis=x] 16` with two oak logs lying
  along x and one standing within 16 blocks
- **THEN** the two lying logs are listed and the standing one is not

#### Scenario: A tag with a state
- **WHEN** an agent runs `find #logs[axis=y] 16` with an oak log and a birch
  log standing and a spruce log lying within 16 blocks
- **THEN** the oak and birch logs are listed and the spruce log is not

### Requirement: Block states are checked
For a block id with states, a state the block lacks or a value its state
doesn't take SHALL fail with `err syntax`, naming the block's states or that
state's values. A state named twice SHALL fail with `err syntax`.

#### Scenario: A state the block lacks
- **WHEN** an agent runs `find stone[axis=x]`
- **THEN** the result is `err syntax` saying stone has no states

#### Scenario: A value the state doesn't take
- **WHEN** an agent runs `find oak_log[axis=up]`
- **THEN** the result is `err syntax` naming the values x, y and z

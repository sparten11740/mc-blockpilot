# Proposal

## Why

`sample` runs one read-only query. Play-tests often need two readouts on the
same ticks: a sub-level's `sable:info` beside a mod's own readout of its
pilot during a landing, or `status` beside `sable:info` to check one height
against the other. Two samples can't be lined up: written one after the other
they run in turn, and `while` takes an action or a `wait`, not a second
sample. So the agent loses the tick-by-tick link between the two readouts.
The two readouts often run on different sides: a mod's readout of what it
simulates runs on the integrated server, while `sable:info` reads the
client's copy of the sub-level.

## What Changes

- `sample` takes several read-only queries joined by `and` before `every`,
  for example `sample sable:info ship brief and status every 20t x30`. Every
  run takes all of them on the same tick.
- With several queries each run gives one row per query, in the order
  written. Each row names its query's command after the tick, as in
  `t758038 sable:info yaw0 …`, and keeps the query's own field names. A
  sample of one query reads as today.
- Queries may run on different sides. A sample that mixes them runs on the
  client's ticks, and on each run it reads its server-side queries together
  on the integrated server's thread, as `side=server` reads do. All of its
  rows carry the client's tick. `clock=server` is refused for such a sample.
- A sample holds at most 1,000 rows: `x<n>` times the number of queries is
  at most 1,000, and a sample beside an action stops there.
- `samples=` still counts runs.
- `sable:info` takes `side=server`, as `sable:find` and `sable:list` do. It
  reads the integrated server's copy of the sub-level: pose, velocity, height
  above ground measured in the server's level, and bounds. This also works
  for a sub-level the client hasn't received. Paired with a server-side
  readout in one sample, both rows then read the copy the server simulates.

## Capabilities

### New Capabilities

None.

### Modified Capabilities
- `command-language`: sampling several queries on the same ticks, also
  across sides, and the grammar's `sample` production (added to the delta
  with the code, see design D1).
- `sable-sublevels`: reading the server's copy of a sub-level with
  `sable:info side=server`.

## Impact

- `protocol/Parser.java`, `Statement.java`, `Engine.java` (the sampler),
  `ScriptJob.java`, `Grammar.java` and `Construct.java` (the usage and help
  text of `sample`), with their tests.
- `protocol/GameBridge.java` and its implementation in `BlockpilotClient`: a
  read on the integrated server's thread for the sampler.
- `game/sable/SableCommands.java` (`sable:info side=server`).
- README: the meta row, the sable row and the With Sable section.

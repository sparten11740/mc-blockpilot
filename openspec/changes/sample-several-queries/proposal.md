# Proposal

## Why

`sample` runs one read-only query. Play-tests often need two readouts on the
same ticks: a sub-level's `sable:info` beside a mod's own readout of its
pilot during a landing, or `status` beside `sable:info` to check one height
against the other. Two samples can't be lined up: written one after the other
they run in turn, and `while` takes an action or a `wait`, not a second
sample. So the agent loses the tick-by-tick link between the two readouts.

## What Changes

- `sample` takes several read-only queries joined by `and` before `every`,
  for example `sample sable:info ship brief and status every 20t x30`. Every
  run takes all of them on the same tick.
- With several queries each run gives one row per query, in the order
  written. Each row names its query's command after the tick, as in
  `t758038 sable:info yaw0 …`, and keeps the query's own field names. A
  sample of one query reads as today.
- The queries must share one clock, the client's or the server's. Queries
  on different clocks are refused with `err syntax`.
- A sample holds at most 1,000 rows: `x<n>` times the number of queries is
  at most 1,000, and a sample beside an action stops there.
- `samples=` still counts runs.

## Capabilities

### New Capabilities

None.

### Modified Capabilities
- `command-language`: sampling several queries on the same ticks, and the
  grammar's `sample` production (added to the delta with the code, see
  design D1).

## Impact

- `protocol/Parser.java`, `Statement.java`, `Engine.java` (the sampler),
  `ScriptJob.java`, `Grammar.java` and `Construct.java` (the usage and help
  text of `sample`), with their tests.
- README: the meta row.

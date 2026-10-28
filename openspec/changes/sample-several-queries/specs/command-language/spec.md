## ADDED Requirements

### Requirement: Sampling several queries
`sample` SHALL take several read-only queries joined by `and` before `every`
and run them all on the same ticks. Each run SHALL give one row per query, in
the order written, each naming its query's command after the tick and keeping
the query's own fields. The queries SHALL share one clock; queries on
different clocks are `err syntax`. A sample SHALL hold at most 1,000 rows, so
`x<n>` times the number of queries is at most 1,000, and a sample beside an
action stops there.

#### Scenario: A body and its pilot on the same ticks
- **WHEN** an agent runs `sample sable:info ship brief and status every 20t
  x3`
- **THEN** the result is `ok samples=3` with six rows: for each of three
  ticks a `sable:info` row and then a `status` row, both with that tick

#### Scenario: One query reads as before
- **WHEN** an agent runs `sample status every 1t x2`
- **THEN** its two rows carry the tick and the `status` fields, and no command
  name

#### Scenario: Queries on different clocks
- **WHEN** an agent runs `sample status and nbt e12 every 1t x5`
- **THEN** the result is `err syntax` saying that `status` runs on the client
  and `nbt` on the server

#### Scenario: Too many rows
- **WHEN** an agent runs `sample status and status every 1t x600`
- **THEN** the result is `err syntax` naming the limit of 1,000 rows

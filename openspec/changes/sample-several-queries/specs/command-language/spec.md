## ADDED Requirements

### Requirement: Sampling several queries
`sample` SHALL take several read-only queries joined by `and` before `every`
and run them all on the same ticks. Each run SHALL give one row per query, in
the order written, each naming its query's command after the tick and keeping
the query's own fields. A sample SHALL hold at most 1,000 rows, so `x<n>`
times the number of queries is at most 1,000, and a sample beside an action
stops there.

#### Scenario: A body and its pilot on the same ticks
- **WHEN** an agent runs `sample sable:info ship brief and status every 20t
  x3`
- **THEN** the result is `ok samples=3` with six rows: for each of three
  ticks a `sable:info` row and then a `status` row, both with that tick

#### Scenario: One query reads as before
- **WHEN** an agent runs `sample status every 1t x2`
- **THEN** its two rows carry the tick and the `status` fields, and no command
  name

#### Scenario: Too many rows
- **WHEN** an agent runs `sample status and status every 1t x600`
- **THEN** the result is `err syntax` naming the limit of 1,000 rows

### Requirement: Sampling both sides at once
A sample whose queries run on different sides SHALL run on the client's ticks
and, on each run, read its server-side queries together on the integrated
server's thread, so all its rows carry the client's tick. `clock=server`
SHALL be refused for such a sample. Without the integrated server, or without
cheats, its server-side rows SHALL carry the error a server-side query gives
there.

#### Scenario: A client and a server readout together
- **WHEN** an agent runs `sample status and nbt e12 every 1t x5` in
  singleplayer with cheats on
- **THEN** the result is `ok samples=5` with five pairs of rows, a `status`
  row and an `nbt` row with the same client tick in each pair

#### Scenario: Server clock asked for
- **WHEN** an agent runs `sample status and nbt e12 every 1t x5 clock=server`
- **THEN** the result is `err syntax` saying that `status` runs on the client

## ADDED Requirements

### Requirement: A side=server read is a server-side query
A query given `side=server` SHALL count as a server-side query wherever
`sample` and `wait` pick a clock: `clock=server` fits it and runs it on the
server thread at the end of the server ticks it samples or checks, reading
the server's level there. In a sample that mixes sides, it SHALL be read in
the run's one trip to the server, with the other server-side queries.

#### Scenario: A sub-level traced on the server clock
- **WHEN** an agent runs `sample sable:info ship brief side=server every 1t
  x20 clock=server`
- **THEN** twenty rows come back, one per server tick, read from the server's
  copy

#### Scenario: One trip for both server readouts
- **WHEN** an agent runs `sample entity e5 and entity e5 side=server and nbt
  e5 path=Pos every 1t x10`
- **THEN** each run reads the second and the third query in one trip to the
  server, so they report the same server tick

### Requirement: The client's tick on client-clock rows
Rows a sample takes on the client clock SHALL carry the client's own tick
count as `client_tick`, printed `c<n>` after the tick, which rises by one per
client tick. `tick` SHALL remain the game time of the one clock. Rows taken on
the server clock carry no client tick.

#### Scenario: Labels that repeat
- **WHEN** the client and the server tick out of phase and an agent runs
  `sample entity e5 every 1t x100`
- **THEN** the rows' `client_tick` rises by one from row to row, even where
  their `tick` repeats or skips

### Requirement: Samples that held the client
A sample on the client clock that read the integrated server while
`load server` was set SHALL report in its result how many runs did,
`held_client=<runs>`, with a note that each such read held the client until
the server's tick ended.

#### Scenario: Smoothness measured under server load
- **WHEN** an agent runs `load server 60; sample entity e5 and entity e5
  side=server every 1t x20`
- **THEN** the result carries `held_client=20`

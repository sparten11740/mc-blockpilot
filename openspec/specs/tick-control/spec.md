# tick-control Specification

## Purpose

Makes timing in experiments deterministic. Agents freeze, step and rate-limit
integrated-server ticks, and sample or wait on server state at exact ticks,
instead of sleeping for wall-clock time.

## Requirements

### Requirement: Freeze, unfreeze and rate
`tick freeze`, `tick unfreeze`, `tick rate <tps>` and `tick query` SHALL control
and report the integrated server's tick state: whether it is frozen, the target
rate, and the current server tick number as `tick`. They follow the
server-bridge gate.

#### Scenario: Freeze and query
- **WHEN** an agent runs `tick freeze; tick query`
- **THEN** the second result reports `frozen=true` and the current server tick
  number

### Requirement: Step returns after the ticks ran
`tick step <n>` SHALL run exactly n server ticks while frozen. It returns only
after the n-th tick has finished, with the new tick number. Stepping while not
frozen fails with `err not_frozen`.

#### Scenario: Step twenty ticks
- **WHEN** the server is frozen at tick 1000 and an agent runs `tick step 20`
- **THEN** the result arrives after tick 1020 has finished and reports
  `tick=1020`

#### Scenario: Step without freeze
- **WHEN** an agent runs `tick step 5` while the server runs normally
- **THEN** the result is `err not_frozen`

### Requirement: Sampling on the server clock
`sample <query> every <Nt> x<n> [clock=client|server]` SHALL run a read-only
query n times, every N ticks, and return one row per run with the tick number
and the query's fields. The keyword `every` separates the query from sample's
own arguments, as `until` does for `wait`. With `clock=server` the query must be
server-side and runs on the server thread at the end of exactly those server
ticks.

#### Scenario: Sample a body's height
- **WHEN** an agent runs `sample nbt e812 path=Pos every 40t x5 clock=server`
- **THEN** five rows come back, at tick numbers 40 ticks apart, each with that
  tick's `Pos`

#### Scenario: Client query on the server clock
- **WHEN** an agent runs `sample status every 20t x3 clock=server`
- **THEN** the result is `err syntax` because `status` is a client-side query

### Requirement: Server-clock waits
`wait <query> until …` SHALL accept `clock=server` for server-side queries, and
use the server clock for every server-side query. Predicates are then evaluated
on the server thread at the end of each server tick, `stable=` and `timeout=`
durations count server ticks, and a successful wait reports the tick it matched
on. While the server is frozen, the wait keeps re-checking on every server loop
iteration and times out normally. `tick step` can't run during it, because
actions run one at a time.

#### Scenario: Wait for rest on the server clock
- **WHEN** an agent runs `wait nbt e812 path=OnGround until value==1 stable=20t
  clock=server timeout=400t` with the server running
- **THEN** the result returns on the server tick where the body has been on the
  ground for 20 ticks, and reports that tick as `t<n>`

#### Scenario: Wait on a frozen server
- **WHEN** an agent runs `tick freeze; wait nbt e812 path=OnGround until
  value==0 clock=server timeout=40t` while the body rests
- **THEN** the wait fails with `err timeout` and the last value after 40 server
  loop iterations, instead of hanging

### Requirement: One tick number in every reply
Every tick number in a reply (`tick query`, `tick step`, sample rows, waits,
and the observation logs with their `since=`) SHALL come from one clock: the
integrated server's game time in singleplayer, otherwise the client level's.

#### Scenario: Ticks agree
- **WHEN** an agent runs `tick query; sounds limit=0; sample status every 1t x1`
- **THEN** all three report the same tick number

### Requirement: Freeze is undone on leave
Leaving or disconnecting from the world SHALL drop any freeze or rate change
Blockpilot applied. A world is never saved in a frozen state because of
Blockpilot.

#### Scenario: Leave while frozen
- **WHEN** an agent runs `tick freeze; leave` and later joins the same world
  again
- **THEN** the world runs at normal speed

### Requirement: Join with ticking frozen
`join world tick=frozen <folder>` SHALL freeze the integrated server's ticking
before its first tick, so that the game time and everything that moves read
as saved until an agent runs `tick unfreeze` or `tick step <n>`. The freeze
is Blockpilot's, so leaving drops it. `join server` with `tick=frozen` SHALL
fail with `err syntax` without connecting.

#### Scenario: Reload a world as saved
- **WHEN** an agent runs `leave` in a singleplayer world, which answers
  `ok saved_at=t27914`, then `join world tick=frozen <folder>; tick query`
- **THEN** `tick query` reports `tick=27914 frozen=true`

#### Scenario: Going on from the saved state
- **WHEN** the agent then runs `tick step 1`
- **THEN** `tick query` reports `tick=27915 frozen=true`

#### Scenario: A server can't be frozen
- **WHEN** an agent runs `join server tick=frozen 0`
- **THEN** the result is `err syntax` and the client stays where it was

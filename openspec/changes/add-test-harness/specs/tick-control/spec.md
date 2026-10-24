# Spec Delta

## Purpose

Makes timing in experiments deterministic. Agents freeze, step and rate-limit integrated-server ticks, and sample or wait on server state at exact ticks, instead of sleeping for wall-clock time.

## ADDED Requirements

### Requirement: Freeze, unfreeze and rate
`tick freeze`, `tick unfreeze`, `tick rate <tps>` and `tick query` SHALL control and report the integrated server's tick state: whether it is frozen, the target rate, and the current server tick number as `tick`. They follow the server-bridge gate.

#### Scenario: Freeze and query
- **WHEN** an agent runs `tick freeze; tick query`
- **THEN** the second result reports `frozen=true` and the current server tick number

### Requirement: Step returns after the ticks ran
`tick step <n>` SHALL run exactly n server ticks while frozen. It returns only after the n-th tick has finished, with the new tick number. Stepping while not frozen fails with `err not_frozen`.

#### Scenario: Step twenty ticks
- **WHEN** the server is frozen at tick 1000 and an agent runs `tick step 20`
- **THEN** the result arrives after tick 1020 has finished and reports `tick=1020`

#### Scenario: Step without freeze
- **WHEN** an agent runs `tick step 5` while the server runs normally
- **THEN** the result is `err not_frozen`

### Requirement: Sampling on the server clock
`sample <query> every <Nt> x<n> [clock=client|server]` SHALL run a read-only query n times, every N ticks, and return one row per run with the tick number and the query's fields. The keyword `every` separates the query from sample's own arguments, as `until` does for `wait`. With `clock=server` the query must be server-side and runs on the server thread at the end of exactly those server ticks.

#### Scenario: Sample a body's height
- **WHEN** an agent runs `sample nbt e812 path=Pos every 40t x5 clock=server`
- **THEN** five rows come back, at tick numbers 40 ticks apart, each with that tick's `Pos`

#### Scenario: Client query on the server clock
- **WHEN** an agent runs `sample status every 20t x3 clock=server`
- **THEN** the result is `err syntax` because `status` is a client-side query

### Requirement: Server-clock waits
`wait <query> until …` SHALL accept `clock=server` for server-side queries. Predicates are then evaluated on the server thread at the end of each server tick, and `stable=` and `timeout=` durations count server ticks. While the server is frozen, a server-clock wait advances only through `tick step`.

#### Scenario: Wait for rest on the server clock
- **WHEN** an agent runs `wait nbt e812 path=Motion[1] until value>-0.001 stable=20t clock=server timeout=400t` with the server running
- **THEN** the result returns on the server tick where the vertical motion has stayed above -0.001 for 20 ticks, and reports that tick number

### Requirement: Freeze is undone on leave
Leaving or disconnecting from the world SHALL drop any freeze or rate change Blockpilot applied. A world is never saved in a frozen state because of Blockpilot.

#### Scenario: Leave while frozen
- **WHEN** an agent runs `tick freeze; leave` and later joins the same world again
- **THEN** the world runs at normal speed

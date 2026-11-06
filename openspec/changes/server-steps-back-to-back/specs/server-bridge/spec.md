## ADDED Requirements

### Requirement: Server steps run back to back
Consecutive steps of commands that run on the server thread, such as `nbt`,
`server` and `tick`, SHALL run back to back: each SHALL start in the server
tick the step before it answered in, without waiting for a client tick, with
their gates checked together before the first. The run SHALL end at the first
other step, which runs on the next client tick: a client step, a `side=server`
read, which runs from the client, or an `if` that holds.

#### Scenario: One server tick apart
- **WHEN** an agent runs `server settle=1t time query gametime` three times in
  one script
- **THEN** the three results are three consecutive game times

#### Scenario: In one server tick
- **WHEN** an agent runs `server settle=0t time query gametime` three times in
  one script
- **THEN** the three results are the same game time

#### Scenario: An if that holds ends the run
- **WHEN** an agent runs `server settle=0t time query gametime; if nbt e<id>
  path=Health value>0 then server settle=0t time query gametime` for a living
  entity
- **THEN** the second `server` result is a later game time than the first

#### Scenario: A client step after the run
- **WHEN** an agent runs `server settle=0t time query gametime; server
  settle=0t time query gametime; status`
- **THEN** both `server` results are the same game time, and `status` answers
  on the client tick after them

## MODIFIED Requirements

### Requirement: Server command results wait for the client
The `server` result SHALL arrive `settle` server ticks after the command ran
(default `2t`), by which time the client has received the command's changes.
`settle=0t` answers at the end of the server tick the command ran in, for
commands that only probe or whose changes the next steps needn't see from the
client.

#### Scenario: Client sees the change
- **WHEN** an agent runs `server setblock 0 -60 0 minecraft:stone; block
  0,-60,0`
- **THEN** the second result is `ok stone`

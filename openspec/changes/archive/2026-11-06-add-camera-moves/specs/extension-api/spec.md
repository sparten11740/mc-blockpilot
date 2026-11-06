## ADDED Requirements

### Requirement: Repeated groups in extension commands
A registration SHALL be able to declare an optional group that repeats up to
a limit, as command-language describes, and its handler SHALL read the values
each slot of the group took, in the order written. This SHALL be an addition
that leaves integrations compiled against the earlier 2.x API working.

#### Scenario: A repeated group in an extension command
- **WHEN** a mod registers `test:path <pos> [via <pos>]…` with a limit of 4
  and an agent runs `test:path 0,0,0 via 1,0,0 via 2,0,0`
- **THEN** the handler reads the via positions `1,0,0` and `2,0,0` in that
  order

#### Scenario: An integration built for 2.0
- **WHEN** a mod compiled against `blockpilot-api` 2.0.0 runs with a
  Blockpilot that has the 2.1.0 API
- **THEN** its commands register and run as before

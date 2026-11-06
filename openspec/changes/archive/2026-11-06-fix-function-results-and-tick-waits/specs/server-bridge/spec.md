## ADDED Requirements

### Requirement: A command without a result succeeds unless it failed
A server command that reports no result, such as a function without
`return`, SHALL answer `ok success=true` without a `result` field, with its
feedback lines as rows, unless it sent a failure message, as a command that
can't be parsed does; then it SHALL answer `err command_failed` with that
message. A command that reports a result SHALL be judged by it, as before.

#### Scenario: A function without return
- **WHEN** an agent runs `server function test:place` for a loaded function
  that sets a block and has no `return`, then `block <that position>
  side=server`
- **THEN** the first result is `ok success=true` with the row `Running
  function test:place`, and the second reads the block the function set

#### Scenario: A function that returns
- **WHEN** an agent runs `server function test:seven` for a function whose
  only command is `return 7`
- **THEN** the result is `ok result=7 success=true`

#### Scenario: An unknown function
- **WHEN** an agent runs `server function test:missing` and no such function
  is loaded
- **THEN** the result is `err command_failed` naming `test:missing`, and the
  later commands in the script are `skip`

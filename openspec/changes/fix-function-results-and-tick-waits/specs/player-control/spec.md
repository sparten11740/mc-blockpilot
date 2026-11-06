## MODIFIED Requirements

### Requirement: Waiting and cancelling
`wait <Nt|Ns>` SHALL wait the given ticks or seconds: the step after it runs
that many ticks after the step before it, and `wait 0t` passes none. `wait
idle` SHALL wait until queued actions finish. `wait <query> until <predicate>`
SHALL wait until a condition holds; see command-language. `stop` SHALL cancel
the running and queued actions and release held inputs.

#### Scenario: One tick between two steps
- **WHEN** an agent runs `wait 1t` forty times in one script
- **THEN** the script takes forty client ticks, about 2 s

#### Scenario: Wait for a block to disappear
- **WHEN** an agent runs `use ~1,0,0; wait block ~1,0,0 until id==air
  timeout=5s` on a block that breaks when used
- **THEN** the script returns as soon as the block is gone, and fails with `err
  timeout` after 5 s if it isn't

#### Scenario: Stop interrupts goto
- **WHEN** session B runs `stop` while session A's `goto` is in progress
- **THEN** A's script result is `err cancelled`, and B's result is `ok stopped
  1`

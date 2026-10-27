## ADDED Requirements

### Requirement: A wait ends on a query syntax error
When the query of `wait <query> until …` fails with `err syntax`, which
running it again cannot change, the wait SHALL end at once with that error
instead of re-running the query until its timeout. Other errors, such as
`unloaded` for a chunk still to arrive, SHALL keep the wait re-running.

#### Scenario: Misspelt block
- **WHEN** an agent runs `wait find stoen 8 until count>0 timeout=60s`
- **THEN** the result is `err syntax` naming `stoen` on the first tick, not
  `err timeout` after 60 s

#### Scenario: Chunk still to arrive
- **WHEN** an agent runs `wait block <position in a chunk the client hasn't
  received> until ok timeout=60s`
- **THEN** the wait keeps re-running `block` through its `err unloaded` and
  returns once the chunk has arrived

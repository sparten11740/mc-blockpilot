## ADDED Requirements

### Requirement: A wait ends on a query syntax error
When the query of `wait <query> until …` fails with `err syntax`, which
running it again cannot change, the wait SHALL end at once with that error
instead of re-running the query until its timeout. Other errors, such as
`not_in_world` while a world loads, SHALL keep the wait re-running.

#### Scenario: Misspelt block
- **WHEN** an agent runs `wait find stoen 8 until count>0 timeout=60s`
- **THEN** the result is `err syntax` naming `stoen` on the first tick, not
  `err timeout` after 60 s

#### Scenario: World still loading
- **WHEN** an agent runs `wait look until ok timeout=60s` while the game joins
  a world
- **THEN** the wait keeps re-running `look` through its `not_in_world` errors
  and returns once the player is in the world

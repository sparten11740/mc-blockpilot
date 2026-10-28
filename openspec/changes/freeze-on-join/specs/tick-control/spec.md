## ADDED Requirements

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

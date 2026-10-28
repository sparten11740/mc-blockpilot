## ADDED Requirements

### Requirement: A stalled client is reported
When the game's client thread has not finished a tick for 30 seconds while no
Blockpilot command holds it, every waiting or running script SHALL be
cancelled and answered at once with `err stalled`, saying how long the client
has not ticked and how many chat lines arrived meanwhile, if any. Requests
that arrive while the stall lasts SHALL be answered the same within a
second.

#### Scenario: Chat flood
- **WHEN** a command block floods chat until the client stops ticking, while
  an agent waits for `wait 100t`
- **THEN** about 30 s after the client's last tick the reply is like
  `err stalled the client hasn't ticked for 30s; 2621440 chat lines arrived
  since`, not a CLI timeout after 300 s

#### Scenario: Asking during the stall
- **WHEN** an agent sends `stop` while the stall lasts
- **THEN** it is answered `err stalled …` within a second

#### Scenario: A long world load
- **WHEN** `create world big normal` holds the client thread for 45 s while
  the world generates
- **THEN** it answers as before: a Blockpilot command holding the client
  thread is not a stall

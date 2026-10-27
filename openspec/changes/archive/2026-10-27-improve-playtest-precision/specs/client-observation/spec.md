# Spec Delta

## ADDED Requirements

### Requirement: Since as a tick or a duration
`since=` on `sounds`, `messages` and `particles` SHALL take either a tick
number, the first tick to include, or a duration such as `60t` or `3s`, which
includes the ticks that long before the current tick.

#### Scenario: The last three seconds
- **WHEN** one sound played 40 ticks ago and another 100 ticks ago, and an
  agent runs `sounds since=3s`
- **THEN** the first sound is listed and the second is not

#### Scenario: A tick from an earlier reply
- **WHEN** an agent runs `messages since=<tick>` with the `tick` of an
  earlier reply
- **THEN** the messages from that tick on are listed, as before

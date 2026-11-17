# Spec Delta

## ADDED Requirements

### Requirement: Sub-level references resolved before a command runs
A `sub:<name|here>` argument SHALL be resolved to `sub:<uuid>` before its
command runs, as `@<name>` is: each time the command runs, also on every
check of a `wait` or `sample`, failing as in the `sable:` commands. Without
Sable, any `sub:` argument SHALL fail with `err unsupported`, naming Sable,
before its command runs.

#### Scenario: An unknown sub-level in a shot list
- **WHEN** an agent runs `camera play hold 5t | follow sub:nosuchship`
- **THEN** the result is `err no_sublevel nosuchship` at once, and the
  camera doesn't change

#### Scenario: Here as the command arrives
- **WHEN** the player stands on a sub-level and an agent runs `camera play
  hold 40t | follow sub:here`, and the player steps off it during the hold
- **THEN** the camera follows the sub-level the player stood on when the
  play arrived

### Requirement: A camera stays when its sub-level goes
A camera placed at a plot position, following or orbiting a sub-level, or
moving round one, SHALL stay where the last frame drew it once the sub-level
is no longer on the client, looking the same way, as a camera following a
removed entity does.

#### Scenario: The ship goes
- **WHEN** `camera follow sub:ship at ~0,8,-20` is placed and the ship is
  removed from the world, and an agent runs `wait 5t; status; wait 1s;
  status`
- **THEN** both `status` replies name the camera at the same position, near
  where it was before the ship went

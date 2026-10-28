# Proposal

## Why

`since=` on `sounds`, `messages` and `particles` takes a tick number or a
duration. A bare number is a game tick, but it reads like "that many ticks
back". `particles since=100` therefore covers everything the particle log
still holds, which stretches back over every tick that had particles, not
the last 100 ticks. An agent took the growing totals for a fix that didn't
work and spent a round on it. Nothing in the reply says which ticks it
covered, so a wrong window looks the same as a right one.

## What Changes

- `sounds`, `messages` and `particles` replies name the window of their log
  they cover, as `window=t<first>..t<now>`. The first tick is the
  `since=` tick, but no earlier than the oldest tick the log still holds;
  without `since=` it is where the default window starts. A wrong window then
  shows: `since=100` on a world at tick 14,159 reads `window=t12959..t14159`.
- When `since=` is a bare tick from before the player joined the current
  world, the reply of these and of `sounds playing` also says so, with the
  tick of joining and a reminder that a duration such as `100t` counts back
  from now.
- A bare number keeps its meaning, so scripts that pass the `tick` of an
  earlier reply work as before.

## Capabilities

### New Capabilities

None.

### Modified Capabilities
- `client-observation`: replies of the observation logs name the window they
  cover, and flag a `since=` tick from before the world was joined. The
  particle counts requirement says what the log keeps: the last 1,200 ticks
  that had particles.

## Impact

- `game/observe/ObservationCommands.java`, `Since.java`, `Observations.java`,
  `TickLog.java` (the oldest tick a log holds, the tick the world was joined).
- `game/BlockpilotClient.java` (noting when the player joins a world).
- Help of the four queries and the README's observe row.

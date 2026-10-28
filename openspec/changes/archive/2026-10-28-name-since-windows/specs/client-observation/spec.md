## ADDED Requirements

### Requirement: The window a reply covers
`sounds`, `messages` and `particles` replies SHALL carry
`window=t<first>..t<now>`, the ticks their answer covers. The first tick is
the `since=` tick, or without `since=` where the default window starts: 20
ticks back for `particles`, the oldest entry for `sounds` and `messages`, or
the current tick when their log is empty. When the log has dropped entries
from that tick on, the first tick is the oldest tick it still holds instead.
The last is the current tick.

#### Scenario: A bare number read as ticks back
- **WHEN** an agent runs `particles since=100` at tick 14,159 and the particle
  log has dropped the counts before tick 12,959
- **THEN** the reply carries `window=t12959..t14159`, not the last 100 ticks

#### Scenario: A duration
- **WHEN** an agent runs `particles since=100t` at tick 14,159
- **THEN** the reply carries `window=t14059..t14159`

#### Scenario: No since
- **WHEN** an agent runs `particles` at tick 14,159
- **THEN** the reply carries `window=t14139..t14159`, the default last 20
  ticks

### Requirement: A since tick from before the world was joined
When `since=` on `sounds`, `sounds playing`, `messages` or `particles` is a
tick number earlier than the tick the player joined the current world, the
reply SHALL say so with `joined=t<tick>` and a reminder that a duration such
as `100t` counts back from now. The query SHALL still answer for the tick it
was given.

#### Scenario: Ticks back written as a tick
- **WHEN** the player joined at tick 12,890 and an agent runs `sounds
  since=100`
- **THEN** the reply lists the sounds from tick 100 on and carries
  `joined=t12890` with the reminder that `100t` counts back

#### Scenario: A tick from an earlier reply
- **WHEN** an agent runs `messages since=<tick>` with the `tick` of a reply
  in this world
- **THEN** the reply carries no `joined=`

## MODIFIED Requirements

### Requirement: Particle counts
The client SHALL count the particles it spawns per particle type and tick, for
the last 1,200 ticks that had any. `particles [since=<tick>]` SHALL return the
count per type over the window, most frequent first, with the total.

#### Scenario: Laser impact particles
- **WHEN** a laser shot spawns 40 crit particles and an agent runs `particles
  since=<tick before the shot>`
- **THEN** a row reports `crit x40`

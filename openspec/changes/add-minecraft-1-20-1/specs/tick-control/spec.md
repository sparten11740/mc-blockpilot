## ADDED Requirements

### Requirement: Tick control without the game's tick rate manager
On Minecraft 1.20.1, which has no tick rate manager, `tick freeze`,
`unfreeze`, `step` and `rate` SHALL behave as on later versions. While frozen,
the game time stays, and only players and what they ride tick, while chunks
still load. The rate SHALL hold on average, also for rates that aren't a whole
number of milliseconds a tick.

#### Scenario: Freeze on 1.20.1
- **WHEN** an agent runs `tick freeze` on a 1.20.1 jar, waits 40 ticks of
  real time and runs `tick query`
- **THEN** it reports `frozen=true` and the same tick number as before, and a
  mob standing in the world hasn't moved

#### Scenario: Step on 1.20.1
- **WHEN** the server is frozen at tick 1000 on a 1.20.1 jar and an agent runs
  `tick step 20`
- **THEN** the result arrives after tick 1020 has finished and reports
  `tick=1020 frozen=true`

#### Scenario: Rate on 1.20.1
- **WHEN** an agent runs `tick rate 30` on a 1.20.1 jar and samples the tick
  number for ten seconds
- **THEN** the server runs about 300 ticks

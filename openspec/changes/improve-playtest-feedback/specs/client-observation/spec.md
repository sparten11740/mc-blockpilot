## ADDED Requirements

### Requirement: Sounds playing now
`sounds playing [since=] [match=] [limit=]` SHALL list the sounds the client's
sound engine is playing: id, category, position or `ui`, volume, pitch,
`loop` for a looping sound, the tick it started and the seconds it has played,
oldest start first, with `count` and the current `tick`. `since=` keeps those
that started from that tick on. A sound that has ended, or that the engine
never started because its volume came out at 0, SHALL not be listed.

#### Scenario: Looping track still playing
- **WHEN** a mod started a looping track a minute ago and an agent runs
  `sounds playing match=*techno*`
- **THEN** a row lists the track with `loop` and about 60 s played, although
  the log may have dropped its start

#### Scenario: Waiting for a fade-out
- **WHEN** an agent runs `wait sounds playing match=*techno* until count==0
  timeout=30s` while the mod fades the track out
- **THEN** the wait returns once the track has stopped, or fails with `err
  timeout`

#### Scenario: Muted category
- **WHEN** the music volume is 0 and a mod starts a music track
- **THEN** `sounds` lists its start, and `sounds playing` does not list it

### Requirement: Log coverage
When the sound or the message log has dropped entries from the window a
`sounds` or `messages` query covers (from `since=` on, or the whole log
without it), the reply SHALL carry `from=<tick>`, the tick of the oldest entry
the log still keeps, so a short or empty list is not taken for all there was.

#### Scenario: Busy scene
- **WHEN** more than 2,000 sounds started since the tick an agent passes as
  `since=`
- **THEN** the `sounds` reply carries `from=` with the tick of the oldest start
  the log still keeps

#### Scenario: Nothing dropped
- **WHEN** an agent runs `sounds since=3s` and the log has dropped only starts
  older than that
- **THEN** the reply carries no `from=`

### Requirement: Unknown sound ids
A `sounds` `match=` without `*` SHALL be a sound event id, in `minecraft:`
when it names no namespace. When the client knows no sound event with that
id, the query SHALL fail with `err syntax unknown sound <id>`, naming up to
three of the closest ids it knows and the `*<name>*` form that matches the
name in any id.

#### Scenario: Bare name
- **WHEN** an agent runs `sounds match=techno` and a mod's sound events
  include `example:music.techno`
- **THEN** the result is `err syntax unknown sound minecraft:techno`, naming
  `example:music.techno` and `match=*techno*`

#### Scenario: Waiting on a misspelt id
- **WHEN** an agent runs `wait sounds match=minecraft:block.anvil.lnad until
  count>0 timeout=30s`
- **THEN** the wait fails at once with `err syntax unknown sound`, naming
  `minecraft:block.anvil.land`

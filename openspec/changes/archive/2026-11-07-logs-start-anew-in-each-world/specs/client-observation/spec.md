## ADDED Requirements

### Requirement: Logs start anew in each world
Entering a world, a singleplayer world (also the one left a moment before) or a
server, SHALL start the sound, message and particle logs anew, so `sounds`,
`messages` and `particles`, their `since=`, the window they cover and `from=`
take in only that world's entries. Between leaving a world and entering the
next, the logs SHALL keep the last world's entries.

#### Scenario: A world made again under an old name
- **WHEN** a world logged a sound at tick 3,911, and an agent leaves it,
  makes a new world under the same name and runs `sounds since=400`
- **THEN** the reply lists only the sounds that started in the new world

#### Scenario: The last world's messages after leaving
- **WHEN** an agent leaves a world and, before entering another, runs
  `messages`
- **THEN** the reply still lists the chat of the world it left

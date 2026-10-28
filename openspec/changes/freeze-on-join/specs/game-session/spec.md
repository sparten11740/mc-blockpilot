## ADDED Requirements

### Requirement: Game time a world was saved at
`leave` and `quit` SHALL answer with `saved_at=t<tick>` when they leave a
singleplayer world: the game time the world was saved at, which is where the
next load of the world starts. Leaving a server, or quitting from a menu,
answers without it.

#### Scenario: Leave a singleplayer world
- **WHEN** an agent runs `leave` in a singleplayer world
- **THEN** the result is `ok saved_at=t<tick>`, and `<tick>` is the game time
  stored in the save's level.dat

#### Scenario: Leave a server
- **WHEN** an agent runs `leave` on a multiplayer server
- **THEN** the result is `ok`, without `saved_at`

## MODIFIED Requirements

### Requirement: Quit the game
`quit` SHALL leave the world as `leave` does (saving singleplayer), then close
the game the way its own Quit Game button does. The reply `ok pid=<pid>`, with
`saved_at=t<tick>` after a singleplayer world, SHALL be sent once the world is
left and before the game closes; the connection then ends. The readiness file
is removed as the game closes. `quit` is an action and waits for the actions
queued before it.

#### Scenario: Quit from a singleplayer world
- **WHEN** an agent runs `quit` in a singleplayer world
- **THEN** the reply is `ok pid=<pid> saved_at=t<tick>`, the world is saved,
  and the game process exits by itself shortly after

#### Scenario: Quit from the title screen
- **WHEN** an agent runs `quit` with no world loaded
- **THEN** the reply is `ok pid=<pid>` and the game closes

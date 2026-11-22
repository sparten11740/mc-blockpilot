## MODIFIED Requirements

### Requirement: Quit the game
`quit` SHALL leave the world as `leave` does (saving singleplayer), then close
the game the way its own Quit Game button does. The reply `ok pid=<pid>`, with
`saved_at=t<tick>` after a singleplayer world, SHALL be sent once the world is
left and before the game closes; the connection then ends. The readiness file
is removed as the game closes. `quit` is an action and waits for the actions
its own session queued before it.

#### Scenario: Quit from a singleplayer world
- **WHEN** an agent runs `quit` in a singleplayer world
- **THEN** the reply is `ok pid=<pid> saved_at=t<tick>`, the world is saved,
  and the game process exits by itself shortly after

#### Scenario: Quit from the title screen
- **WHEN** an agent runs `quit` with no world loaded
- **THEN** the reply is `ok pid=<pid>` and the game closes

#### Scenario: Quit after the session's own actions
- **WHEN** an agent sends `goto 10 64 10` and then, before it answers, `quit`
  on the same connection
- **THEN** the walk runs to its end and answers first, then `quit` answers
  `ok pid=<pid> saved_at=t<tick>`

## ADDED Requirements

### Requirement: Quit doesn't wait for other sessions
The actions other sessions queued before `quit` SHALL be cancelled with `err
cancelled`, as `stop` cancels them, so `quit` goes next even while another
session's `create world` holds the game. A world being created can't be left
part way: the reply then comes once the game shows the world's loading
progress, and the game closes once the world has loaded.

#### Scenario: Quit while another session creates a world
- **WHEN** session B runs `quit` while session A's `create world mymod-test`
  is loading the world
- **THEN** A's result is `err cancelled`, B's reply is `ok pid=<pid>`, and the
  game process exits by itself once the world has loaded

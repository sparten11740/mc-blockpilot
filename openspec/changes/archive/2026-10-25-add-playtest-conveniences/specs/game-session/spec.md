# Spec Delta

## ADDED Requirements

### Requirement: Quit the game
`quit` SHALL leave the world as `leave` does (saving singleplayer), then close
the game the way its own Quit Game button does. The reply `ok pid=<pid>` SHALL
be sent once the world is left and before the game closes; the connection then
ends. The readiness file is removed as the game closes. `quit` is an action and
waits for the actions queued before it.

#### Scenario: Quit from a singleplayer world
- **WHEN** an agent runs `quit` in a singleplayer world
- **THEN** the reply is `ok pid=<pid>`, the world is saved, and the game
  process exits by itself shortly after

#### Scenario: Quit from the title screen
- **WHEN** an agent runs `quit` with no world loaded
- **THEN** the reply is `ok pid=<pid>` and the game closes

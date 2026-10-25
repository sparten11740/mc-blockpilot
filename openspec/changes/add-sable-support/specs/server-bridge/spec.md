# Spec Delta

## ADDED Requirements

### Requirement: A paused game fails server work at once
While singleplayer is paused, the integrated server runs no ticks. A script
step that runs on the server thread or waits on the server clock SHALL then
fail with `err paused` on the next client tick instead of waiting, and the
actions queued after it SHALL run. A server action already running is
cancelled on the server thread.

#### Scenario: Server command behind the game menu
- **WHEN** the game menu is open in singleplayer, an agent runs
  `server tp @p 0 64 0` and, in a second request, `click w0` on Back to Game
- **THEN** the first result is `err paused`, and the `click` runs and closes
  the menu

# game-session Specification

## Purpose

Lets an agent get the client into and out of a game (singleplayer worlds and
multiplayer servers) without a human clicking through menus, and reports the
connection state.

## Requirements

### Requirement: List worlds and servers
`worlds` SHALL list singleplayer saves (folder name, display name, game mode,
last played). `servers` SHALL list entries from the multiplayer server list
(index, name, address). Both work from any screen.

#### Scenario: List saves
- **WHEN** an agent runs `worlds` from the title screen
- **THEN** each save is listed on one line, most recently played first

### Requirement: Join a game
`join world <folder>` SHALL load a singleplayer save. `join server
<index|address>` SHALL connect to a server. The result returns once the player
is in the world, or fails with `err join_failed <reason>`. Joining while already
in a game first leaves the current one.

#### Scenario: Join a save
- **WHEN** an agent runs `join world New World`
- **THEN** the world loads, and the result is `ok joined New World` once the
  player can act

#### Scenario: Server unreachable
- **WHEN** an agent runs `join server 10.0.0.9:25565` and the server does not
  answer
- **THEN** the result is `err join_failed <disconnect reason>` and the client is
  back on a menu screen

### Requirement: Leave a game
`leave` SHALL save and quit singleplayer, or disconnect from a server, and
return to the title screen.

#### Scenario: Leave singleplayer
- **WHEN** an agent runs `leave` in a singleplayer world
- **THEN** the world is saved and the title screen shows

### Requirement: Session events
Being disconnected, kicked, killed (death screen) and respawning SHALL be
reported as `!` events. `respawn` SHALL respawn from the death screen.

#### Scenario: Death and respawn
- **WHEN** the player dies
- **THEN** a `! died <cause>` event is delivered, and `respawn` returns the
  player to the world

### Requirement: Endpoint independent of world state
The endpoint and the session commands SHALL be available from client startup
onward, whether or not a world is loaded. World-dependent commands fail with
`err not_in_world` outside a world.

#### Scenario: Mine from title screen
- **WHEN** an agent runs `mine ~0,-1,0` while on the title screen
- **THEN** the result is `err not_in_world`

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

### Requirement: Worlds using experimental settings
When the game asks, before loading a world that `join world` or `snapshot save`
or `restore` opens, whether to back it up as it uses experimental settings,
Blockpilot SHALL load a test world made by `create world` without a backup,
adding `experimental=true`, and SHALL fail with `err join_failed` for any other
world, the question left on screen. Other backup questions SHALL stay for a
person.

#### Scenario: A test world with a data pack's GameTests
- **WHEN** an agent runs `join world <folder>` on 26.x for a test world whose
  data pack adds GameTests, which the game counts as experimental settings
- **THEN** the world loads without the question waiting, and the result is
  `ok joined <folder> experimental=true`, `<folder>` being the save folder's
  name, as in `ok joined New World`

#### Scenario: A player's own world
- **WHEN** an agent runs `join world <folder>` for a world not made by
  `create world`, about which the game asks the same question
- **THEN** the result is `err join_failed` naming the question, which stays on
  screen for a person to answer

# Spec Delta

## Purpose

Lets an agent get the client into and out of a game (singleplayer worlds and
multiplayer servers) without a human clicking through menus, and reports the
connection state.

## ADDED Requirements

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

# Spec Delta

## Purpose

Gives agents direct, structured access to the integrated server in singleplayer:
running commands with captured results, and reading blocks, entities and NBT the
client can't see. This replaces RCON and text parsing in mod play-testing.

## ADDED Requirements

### Requirement: Server access is gated
Every command that reads or changes integrated-server state SHALL work only
while an integrated server is running and the current world allows commands.
Without an integrated server it fails with `err no_server`. In a world without
commands allowed it fails with `err no_cheats`.

#### Scenario: On a remote server
- **WHEN** an agent connected to a multiplayer server runs `server time set day`
- **THEN** the result is `err no_server` and nothing is sent to the remote
  server

#### Scenario: Survival world without cheats
- **WHEN** an agent in a singleplayer world with commands disabled runs `nbt
  e812`
- **THEN** the result is `err no_cheats`

### Requirement: Run server commands with captured results
`server <command>` SHALL run the command on the integrated server thread with
permission level 4. The result carries the command's return value as `result`,
whether it succeeded as `success`, and every feedback message sent to the
command source as rows. `as=player` runs it as the player, positioned at the
player, instead of the server source.

#### Scenario: Probe a block
- **WHEN** an agent runs `server execute if block 0 -60 0 minecraft:air`
- **THEN** the result is `ok result=1 success=true` with the feedback line `Test
  passed` as a row

#### Scenario: Failing command
- **WHEN** an agent runs `server setblock 0 -60 0 not_a_block`
- **THEN** the result is `err command_failed` with the command's error message,
  and later commands in the script are `skip`

#### Scenario: Mod command output
- **WHEN** an agent runs a mod's info command through `server`
- **THEN** all feedback lines the mod sends to the source come back as rows, in
  order, in one reply

### Requirement: Server-side block and entity reads
`block`, `blocks` and `find` SHALL accept `side=server` to read the integrated
server's level instead of the client's. This covers chunks the server has loaded
(including forceloaded ones) but the client hasn't. Server-side entity results
add a `uuid` field. Reading an unloaded position fails with `err unloaded` and
never loads or generates chunks.

#### Scenario: Forceloaded chunk far away
- **WHEN** a chunk 2,000 blocks away is forceloaded and an agent runs `block
  2000,-60,0 side=server`
- **THEN** the block there is returned, although the client has not loaded that
  chunk

#### Scenario: Unloaded chunk
- **WHEN** an agent runs `block 90000,64,0 side=server` for a chunk nobody has
  loaded
- **THEN** the result is `err unloaded`, and no chunk is generated

### Requirement: NBT queries
`nbt <pos|e<id>|uuid> [path=<nbt path>]` SHALL return the full server-side data
of a block entity or entity. Text mode shows SNBT, JSON mode a JSON tree. `path`
selects a sub-element with vanilla NBT path syntax. Output over 8 KB is cut off
with a `truncated` marker and the total size.

#### Scenario: Entity position
- **WHEN** an agent runs `nbt e812 path=Pos`
- **THEN** the result is `ok [12.5d,-59.0d,3.5d]` in text mode, or
  `{"ok":true,"value":[12.5,-59.0,3.5]}` in JSON mode

#### Scenario: Large block entity
- **WHEN** a block entity's NBT is 40 KB
- **THEN** `nbt <pos>` returns the first 8 KB, a `truncated 40960` marker, and
  help suggests `path=` to narrow the query

### Requirement: Server reads are consistent within a tick
A server-side query SHALL run in one piece on the server thread, between ticks.
Its values never mix two different ticks.

#### Scenario: Multi-entity read
- **WHEN** an agent runs `find armor_stand 64 side=server` while armor stands
  move
- **THEN** every returned position belongs to the same server tick

# server-bridge Specification

## Purpose

Gives agents direct, structured access to the integrated server in singleplayer:
running commands with captured results, and reading blocks, entities and NBT the
client can't see. This replaces RCON and text parsing in mod play-testing.

## Requirements

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
`server [as=player] [settle=<Nt>] <command>` SHALL run the command on the
integrated server thread with permission level 4. Options come before the
command text, which runs to the end of the command. The result carries the
command's return value as `result`, whether it succeeded as `success`, and every
feedback message sent to the command source as rows. `as=player` runs it as the
player, positioned at the player, instead of the server source.

#### Scenario: Probe a block
- **WHEN** an agent runs `server execute if block 0 -60 0 minecraft:air`
- **THEN** the result is `ok result=1 success=true` with the feedback line `Test
  passed` as a row

#### Scenario: Failing command
- **WHEN** an agent runs `server setblock 0 -60 0 not_a_block`
- **THEN** the result is `err command_failed Unknown block type
  'minecraft:not_a_block'`, Brigadier's `…<--[HERE]` pointer appears only as a
  row, and later commands in the script are `skip`

#### Scenario: Mod command output
- **WHEN** an agent runs a mod's info command through `server`
- **THEN** all feedback lines the mod sends to the source come back as rows, in
  order, in one reply

### Requirement: A command without a result succeeds unless it failed
A server command that reports no result, such as a function without
`return`, SHALL answer `ok success=true` without a `result` field, with its
feedback lines as rows, unless it sent a failure message, as a command that
can't be parsed does; then it SHALL answer `err command_failed` with that
message. A command that reports a result SHALL be judged by it, as before.

#### Scenario: A function without return
- **WHEN** an agent runs `server function test:place` for a loaded function
  that sets a block and has no `return`, then `block <that position>
  side=server`
- **THEN** the first result is `ok success=true` with the row `Running
  function test:place`, and the second reads the block the function set

#### Scenario: A function that returns
- **WHEN** an agent runs `server function test:seven` for a function whose
  only command is `return 7`
- **THEN** the result is `ok result=7 success=true`

#### Scenario: An unknown function
- **WHEN** an agent runs `server function test:missing` and no such function
  is loaded
- **THEN** the result is `err command_failed` naming `test:missing`, and the
  later commands in the script are `skip`

#### Scenario: A function on 1.20.1
- **WHEN** an agent runs `server function test:place` on a 1.20.1 jar, for a
  function without `return` that runs one command
- **THEN** the result is `ok result=1 success=true` with the row `Executed 1
  command(s) from function 'test:place'`, as 1.20.1's game counts the commands
  a function ran as its result

### Requirement: Server command results wait for the client
The `server` result SHALL arrive `settle` server ticks after the command ran
(default `2t`), by which time the client has received the command's changes.
`settle=0t` answers at the end of the server tick the command ran in, for
commands that only probe or whose changes the next steps needn't see from the
client.

#### Scenario: Client sees the change
- **WHEN** an agent runs `server setblock 0 -60 0 minecraft:stone; block
  0,-60,0`
- **THEN** the second result is `ok stone`

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
Its values never mix two different ticks. Consecutive server-side queries in one
script, up to the next action, SHALL all run in the same server tick.

#### Scenario: Many server queries
- **WHEN** an agent sends a script of 1,000 `nbt <uuid> path=Pos` queries
- **THEN** all 1,000 results come from one server tick and the reply arrives
  within that tick, without the client frame rate dropping

#### Scenario: Multi-entity read
- **WHEN** an agent runs `find armor_stand 64 side=server` while armor stands
  move
- **THEN** every returned position belongs to the same server tick

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

#### Scenario: Server command right after the menu closes
- **WHEN** the game menu is open in singleplayer and an agent runs
  `close; server time query gametime`
- **THEN** the `server` step waits for the server's next tick, which takes the
  unpause, and runs, rather than failing with `err paused`

### Requirement: Server commands run from the player
Without `as=player`, `server` SHALL run the command as the server's own
source, positioned at the player with the player's rotation and in the
player's dimension, so `~`, `^`, `@p` and `distance=` count from the player.
When no player is in the world, it SHALL run at the overworld's spawn.

#### Scenario: Relative block
- **WHEN** an agent standing far from the world spawn runs `server setblock ~
  ~-1 ~ minecraft:stone`
- **THEN** the block under the player becomes stone

#### Scenario: Entities near the player
- **WHEN** a dropped item lies next to the player, none lies near the spawn,
  and an agent runs `server execute if entity @e[type=item,distance=..8]`
- **THEN** the result is `ok result=1 success=true`

#### Scenario: Another dimension
- **WHEN** an agent in the Nether runs `server setblock ~ ~-1 ~
  minecraft:stone`
- **THEN** the block under the player in the Nether becomes stone, and the
  overworld is unchanged

### Requirement: Server steps run back to back
Consecutive steps of commands that run on the server thread, such as `nbt`,
`server` and `tick`, SHALL run back to back: each SHALL start in the server
tick the step before it answered in, without waiting for a client tick, with
their gates checked together before the first. The run SHALL end at the first
other step, which runs on the next client tick: a client step, a `side=server`
read, which runs from the client, or an `if` that holds.

#### Scenario: One server tick apart
- **WHEN** an agent runs `server settle=1t time query gametime` three times in
  one script
- **THEN** the three results are three consecutive game times

#### Scenario: In one server tick
- **WHEN** an agent runs `server settle=0t time query gametime` three times in
  one script
- **THEN** the three results are the same game time

#### Scenario: An if that holds ends the run
- **WHEN** an agent runs `server settle=0t time query gametime; if nbt e<id>
  path=Health value>0 then server settle=0t time query gametime` for a living
  entity
- **THEN** the second `server` result is a later game time than the first

#### Scenario: A client step after the run
- **WHEN** an agent runs `server settle=0t time query gametime; server
  settle=0t time query gametime; status`
- **THEN** both `server` results are the same game time, and `status` answers
  on the client tick after them

### Requirement: Running GameTests
`gametest run <selector>` SHALL run the registered GameTests whose ids match the
selector, where `*` matches any run of characters, on the integrated server, and
answer once they have finished with a row per test, `passed` or `failed` with
its message.

#### Scenario: A namespace's tests
- **WHEN** an agent runs `gametest run blockpilot_smoke:*` in a 26.x world
  with the smoke check's data pack, one test passing and one failing
- **THEN** the reply lists the passing test as `passed` and the failing one
  as `failed` with its message

#### Scenario: Tests in code on 1.21.1
- **WHEN** an agent runs `gametest run blockpilotsmoketests.*` in a 1.21.1
  dev client, with Blockpilot's own dev tests registered
- **THEN** the reply lists `blockpilotsmoketests.passes` as `passed` and
  `blockpilotsmoketests.fails` as `failed` with its message

### Requirement: When GameTests can't run
Without an integrated server `gametest run` SHALL fail with `err no_server`, and
on NeoForge without GameTests enabled with `err unsupported` naming what enables
them, whatever the selector; otherwise a selector that matches no test SHALL
fail with `err no_test`.

#### Scenario: No such test
- **WHEN** an agent runs `gametest run nothing_here:*`
- **THEN** the result is `err no_test`

#### Scenario: Not enabled
- **WHEN** an agent runs `gametest run *` on NeoForge without
  `-Dneoforge.enableGameTest=true`
- **THEN** the result is `err unsupported` naming that property

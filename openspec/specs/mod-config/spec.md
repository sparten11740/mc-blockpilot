# mod-config Specification

## Purpose

Lets agents read and set mods' own config values from a script while the game
runs, and puts back what they changed, so a play-test can flip a mod's setting
without editing its files.

## Requirements

### Requirement: Listing mod configs
`config` SHALL list the mods whose config files the loader keeps, each with
its config types (`client`, `common`, `server`, `startup`) and file names.
`config <mod> [<type>] [match=<pattern>]` SHALL list that mod's values by
their dotted keys as in the file, with each value, and with `was` and the
original for each one an agent changed.

#### Scenario: A mod's client values
- **WHEN** an agent runs `config example client`
- **THEN** a row lists each key of the mod's client config with its value

#### Scenario: Unknown mod
- **WHEN** an agent runs `config exmaple`
- **THEN** the result is `err syntax` naming `example` as the closest mod with
  config files

### Requirement: Reading a mod config value
`config <mod> [<type>] <key>` SHALL report the value, its default, what it
takes (`true|false`, a range, the allowed names, a number, text or a list),
`restart=world|game` when a change only takes effect after that restart, and
the mod's comment on it. The type MAY be left out when only one of the mod's
files has the key. An unknown key SHALL fail with `err syntax` naming the
closest keys.

#### Scenario: Value with a range
- **WHEN** an agent runs `config example common spawnLimit`
- **THEN** the reply gives its value, its default and the range it takes

#### Scenario: Key in two files
- **WHEN** a mod has a key `debug` in both its client and its common config and
  an agent runs `config example debug`
- **THEN** the result is `err syntax` naming both types

### Requirement: Setting a mod config value
`config <mod> [<type>] <key> <value>` SHALL set the value in the running game,
tell the mod as a reload of its file does, and answer after the mod has been
told, with the new value and `was` the value before the first agent change.
Blockpilot SHALL NOT write config files. Values are written as in the file:
`true|false` (or `on|off`), numbers, allowed names, text, lists separated by
commas. A value the key doesn't take SHALL fail with `err syntax` naming what
it takes.

#### Scenario: Switch flipped mid-test
- **WHEN** an agent runs `config example client floodlights false`
- **THEN** the mod's reload handler has run when the reply comes, the reply
  reads `client floodlights false was true …`, and the file still says `true`

#### Scenario: Value out of range
- **WHEN** an agent runs `config example common spawnLimit 999` and the key
  takes 0 to 64
- **THEN** the result is `err syntax` naming the range

### Requirement: Values that need a restart
A value that the mod marks as needing a world restart SHALL be set in client
and common configs, with `restart=world` in the reply. It takes effect at the
next world load, and Blockpilot's own world switches keep it. A value marked
as needing a game restart, or a server config value marked as needing a world
restart, SHALL fail with `err needs_restart` naming the file to edit, since
an unwritten change can't outlast either.

#### Scenario: World restart in a client config
- **WHEN** an agent sets a client value the mod marks for a world restart and
  then runs `join` on the same world
- **THEN** the reply to the set carries `restart=world`, and the mod reads the
  new value in the rejoined world

#### Scenario: Game restart
- **WHEN** an agent sets a value the mod marks for a game restart
- **THEN** the result is `err needs_restart` naming the mod's config file

### Requirement: Server configs
A server config SHALL be readable while a world is loaded, on a remote server
as the copy it sent. Setting one SHALL need the integrated server and fail on
a remote one with `err no_server`. Outside a world, naming a server config
SHALL fail with `err not_in_world`. A change to a server config lasts until
the world closes, because each world load reads that world's file again.

#### Scenario: Remote server
- **WHEN** an agent connected to a dedicated server runs `config example
  server spawnLimit 8`
- **THEN** the result is `err no_server`

### Requirement: Agent config changes end with agent control
Config values agents changed SHALL be put back, and the mod told again, on
`config reset` and wherever agent option changes are restored: on `leave`,
when the world is left in a way Blockpilot did not start, when the
agent-control key turns agent control off, and when the client shuts down.
Blockpilot's own world switches keep them.

#### Scenario: Reset after the test
- **WHEN** an agent set `floodlights false` and then runs `config reset`
- **THEN** `floodlights` is `true` again and the mod's reload handler has run

### Requirement: Mod configs need NeoForge
On a loader without NeoForge's config system, such as Fabric, `config` SHALL
fail with `err unsupported`.

#### Scenario: Fabric
- **WHEN** an agent runs `config` on the Fabric build
- **THEN** the result is `err unsupported`

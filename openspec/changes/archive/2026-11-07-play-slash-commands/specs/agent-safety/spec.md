# Spec Delta

## MODIFIED Requirements

### Requirement: Play-only agents
With `agentCommands` set to `play` in the config, a script with any of
`server`, `nbt`, `tick`, `gametest`, `snapshot`, `config`, `record` in any
form, `perf` in any form but `perf status`, `load`, `create world`,
`delete world`, `join`, `leave` or `quit`, or a `cmd` whose root isn't in
`playSlashCommands`, SHALL fail before running any step, with `err
not_allowed <command>`. Other commands SHALL work as before. The default,
`all`, SHALL refuse nothing.

#### Scenario: A slash command refused
- **WHEN** `agentCommands` is `play` and an agent runs `goto 0,64,0; cmd
  op Mallory`
- **THEN** the result is `err not_allowed cmd op`, the player hasn't
  moved, and no command was sent

#### Scenario: Playing goes on
- **WHEN** `agentCommands` is `play` and an agent runs `mine #logs x3`
- **THEN** the logs are mined as before

#### Scenario: Captures to disk refused, reading allowed
- **WHEN** `agentCommands` is `play` and an agent runs `perf 1s`, then
  `perf status`
- **THEN** `perf 1s` fails with `err not_allowed perf`, and `perf status`
  answers as before

### Requirement: No slash commands sent for play-only agents
With `agentCommands` set to `play`, a slash command the agent sends any
other way, typed into the chat screen or clicked in a chat message, SHALL
NOT be sent unless its root is in `playSlashCommands`, and the command that
sent it SHALL fail with `err not_allowed cmd <root>`. Slash commands a
person sends SHALL go out as usual.

#### Scenario: Typed into the chat screen
- **WHEN** `agentCommands` is `play` and an agent opens chat, types `/say
  hi` and presses enter
- **THEN** nothing is sent, and the `key` command fails with `err
  not_allowed cmd say`

#### Scenario: A listed root typed
- **WHEN** `agentCommands` is `play`, `playSlashCommands` lists `fill`,
  and an agent types `/fill ~ ~1 ~ ~ ~1 ~ stone` and presses enter
- **THEN** the command goes out, and the `key` command answers `ok`

### Requirement: Agents read trust
The query `trust` SHALL list the trusted players, each with whether they
are online, and report `unverified` when the server doesn't verify
accounts. With `dangerouslyAllowAll` on it SHALL report
`dangerously_allow_all` and list no names, as everyone counts as trusted.
It SHALL take no arguments.

#### Scenario: Reading the list
- **WHEN** the list holds `Steve`, who is online, and an agent runs
  `trust`
- **THEN** the reply lists `Steve` as online

#### Scenario: Everything allowed
- **WHEN** `dangerouslyAllowAll` is on and the list holds `Steve`
- **THEN** `trust` reports `dangerously_allow_all` and lists no names

## ADDED Requirements

### Requirement: Slash commands a play-only agent may send
`playSlashCommands` in the config file, empty by default, SHALL list the
slash command roots a play-only agent may send by `cmd`, typed or clicked:
a command's first word without its leading `/` and an optional
`minecraft:` prefix, matched ignoring case. It SHALL be set in the file
only and take effect within a second of a change.

#### Scenario: A listed root
- **WHEN** `agentCommands` is `play`, `playSlashCommands` lists `fill`,
  and an agent runs `cmd /minecraft:FILL ~ ~ ~ ~ ~ ~ stone`
- **THEN** the command is sent

#### Scenario: A root that isn't listed
- **WHEN** `playSlashCommands` lists only `fill` and an agent runs `cmd op
  Mallory` under `play`
- **THEN** the result is `err not_allowed cmd op`

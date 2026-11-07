# Proposal

## Why

The play profile refuses every slash command, so an agent that builds with
a child can't use `/fill` or `/setblock`, though those are the point of the
session. And since 1.1.0, other mods' commands need `allowedExtensions`
even when no safety is wanted, which got in the way of testing integrations
under the default `all`.

## What Changes

- `playSlashCommands` in the config file: slash command roots a play-only
  agent may send, empty by default, cleaned and reloaded like the other
  lists. Under `play`, `cmd` and a slash command typed or clicked go
  through only when the command's first word, without `/` and an optional
  `minecraft:`, is listed, ignoring case; a refusal names the root
  (`err not_allowed cmd op`).
- `allowedExtensions` applies only under `play`: under `all` every mod's
  commands run, and extension events are always delivered. Help marks a
  namespace as not allowed only under `play`.
- The spec says what the code does with `dangerouslyAllowAll` on: `trust`
  reports `dangerously_allow_all` and lists no names.
- The docs warn never to list `execute` or `function`, which run any other
  command, and suggest a world backup before building with `fill`.

## Capabilities

### New Capabilities

None.

### Modified Capabilities
- `agent-safety`: slash commands by root under `play`; `trust` with
  `dangerouslyAllowAll`.
- `extension-api`: the allow-list applies only under `play`; events are
  always delivered.

## Impact

- `protocol`: `SafetyPolicy.slashCommandAllowed` and the root parsing,
  `ConfiguredPolicy`, `CommandGate` (which now reads `cmd`'s argument),
  `Engine` (events), `Config`.
- `game/safety/PlayGuard`: the send hooks ask the policy per root.
- Tests, `tools/security_smoke_test.py`, docs/safety.md, commands.md,
  extending.md, the README and the CHANGELOG's 1.1.1 section.

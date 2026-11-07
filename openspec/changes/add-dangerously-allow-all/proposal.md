# Proposal

## Why

Blockpilot's agent safety (trusted players, allowed extensions, the play
profile) protects an agent that plays with other people. Testing a mod
alone, in single-player or with a mod's own integration, it is in the
way: every tester has to list their mods and their own second client
first. A person should be able to turn it all off at once, knowingly.

## What Changes

- `dangerouslyAllowAll` in `config/blockpilot/config.json`, a boolean,
  false by default. True turns every agent safety check off:
  - every player's chat reaches agents with its text, unverified servers
    included, and `BlockpilotApi.isTrusted` answers true for everyone
  - every extension's commands and events are used, whatever
    `allowedExtensions` says
  - `agentCommands` is ignored, as if `all`: no command, screen or link
    refusals, and slash commands go out as before
- Set in the file only, reloaded within a second like the other safety
  settings, never changed by a command. A file that can't be read counts
  as false.
- While it is on, Blockpilot logs a warning at start and on each change,
  `trust` reports `dangerously_allow_all`, and the agent indicator on
  screen shows "safety off" (hidden while recording, like the rest of it).
- `docs/safety.md`, `docs/commands.md`, the README's Security section and
  the CHANGELOG call it dangerous and say when it is fine: testing a mod
  alone or in single-player only.
- A security smoke check: with the flag on, `devcheck:ping` runs without
  `allowedExtensions`, a command `play` refuses runs, and `trust` reports
  the flag.

## Capabilities

### New Capabilities
- `agent-safety`: the switch that turns every agent safety check off.

### Modified Capabilities

None.

## Impact

- `protocol/Config.java`: `dangerouslyAllowAll`; `playOnly()` false while
  it is on.
- `Engine` (extensions and events), `Trust`, `SafetyCommands`, the client's
  settings reload and its HUD indicator.
- `tools/security_smoke_test.py`, the docs and the CHANGELOG.

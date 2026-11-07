# Proposal

## Why

An agent that plays with other people takes requests from what they say:
chat today, and speech once the voice add-on is in. Blockpilot passes it
every player's chat, so on a server with strangers anyone can tell the
agent what to do. With the agent player's rights, that means slash commands
such as `/op` or `/fill` on a world with cheats, deleting worlds, or
closing the game. Nothing in Blockpilot limits who may instruct the agent or
what it may do in the game. The docs don't warn about it either.

## What Changes

- **BREAKING** (agent-facing behaviour, still in 1.1.0): a list of trusted
  players in Blockpilot's config file, empty by default. Chat from any
  player not on it, the agent's own player aside, reaches agents without
  its text: `! chat withheld from=<name>`, and the same in `messages`. On a
  server, agents see other players' chat text only once those players are
  trusted. Single-player play and testing are unaffected, and system and
  mod messages pass as before.
- **BREAKING** for integrations: another mod's commands and events reach
  agents only while its namespace is in `allowedExtensions` in the config,
  empty by default; Blockpilot's own compat modules need none. A call that
  isn't allowed fails with `err not_allowed <namespace>:<name>`, its events
  are dropped, and `help` names the setting.
- Trust is set in `config/blockpilot/config.json` only. A change to the
  file takes effect within a second, without a restart. No command, in the
  game or from an agent, changes it.
- On a server that doesn't verify accounts, where anyone can take any
  name, nobody but the agent's own player counts as trusted. Only a
  development run launched with `-Pblockpilot.trustUnverified` applies the
  list there, so offline dev accounts can test it.
- The read-only query `trust` tells agents whom the list trusts.
- `agentCommands: "play"` in the config limits agents to playing as a
  player. Commands that reach beyond the game in front of them are refused
  with `err not_allowed <command>`: slash commands, the integrated server,
  world files, mod configs, files written to disk, switching worlds or
  servers, and quitting. A slash command an agent types into the chat
  screen or clicks in chat isn't sent. The default stays `all`, as testing
  mods needs these commands.
- `blockpilot-api` 2.1.0 lets extensions ask whether a player is trusted.
  The voice add-on then hears only trusted players. 2.1.0 isn't released
  yet, so this needs no new version.
- `docs/safety.md` covers playing with others:
  - what each setting does
  - that text from players is untrusted input, chat with senders the
    server relays as system messages included
  - the agent's own setup, checked against Claude Code's docs: Blockpilot's
    tools only, no bypass modes, and a separate user, VM or container as
    the strongest option
- A short "Security" section in the README, and `docs/commands.md` and the
  CHANGELOG, say the same.
- `tools/security_smoke_test.py` checks all of it on every target, on its
  own or after the smoke test.

## Capabilities

### New Capabilities
- `agent-safety`: trusted players set in the config file, empty by default
  and reloaded on change; withheld chat; unverified servers; and the
  play-only command profile.

### Modified Capabilities
- `extension-api`: extensions can read whether a player is trusted.

## Impact

- `protocol/Config.java`: `trustedPlayers` and `agentCommands`, reread
  when the file changes.
- The chat hooks of both loaders and the vanilla targets: the sender
  passed with player chat, so `Observations` and `Events` can withhold
  its text.
- `Engine`: the play profile's refusals at parse time. A mixin on the
  client's slash-command sending, and `Refusal`, through which a dropped
  command fails the step that sent it.
- `dev.blockpilot.api`: `BlockpilotApi.isTrusted(UUID)`.
- `tools/security_smoke_test.py`, and `--suite` in `tools/test_targets.py`.
- `docs/safety.md`, the README, `docs/commands.md`, `docs/extending.md`,
  `docs/dev-environment.md` and the CHANGELOG.

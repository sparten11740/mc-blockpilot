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
- A person at the agent's game can change the list in its config, or with
  `/blockpilot trust <player>`, `/blockpilot untrust <player>` and
  `/blockpilot trusted` typed in chat. A change is saved to the config at
  once. These commands take effect only when a person types them. Sent for
  an agent, through `cmd`, `chat` or keys typed into the chat screen, they
  are refused with `err not_allowed`, and no agent command changes the
  list.
- On a server that doesn't verify accounts, where anyone can take any
  name, nobody but the agent's own player counts as trusted.
- The read-only query `trust` tells agents whom the list trusts.
- `agentCommands: "play"` in the config limits agents to playing as a
  player. Commands that reach beyond the game in front of them are refused
  with `err not_allowed <command>`: slash commands, the integrated server,
  world files, mod configs, files written to disk, switching worlds or
  servers, and quitting. A slash command typed into the chat screen on an
  agent's behalf isn't sent. The default stays `all`, as testing mods needs
  these commands.
- `blockpilot-api` 2.1.0 lets extensions ask whether a player is trusted.
  The voice add-on then hears only trusted players. 2.1.0 isn't released
  yet, so this needs no new version.
- `docs/safety.md` covers playing with others:
  - what each setting does
  - that text from players is untrusted input, chat with senders the
    server relays as system messages included
  - that the agent taking requests from players should run with
    Blockpilot's tools only, with a ready permission setup for Claude Code
- The README, `docs/commands.md` and the CHANGELOG say the same.

## Capabilities

### New Capabilities
- `agent-safety`: trusted players, empty by default, and how a person sets
  them; withheld chat; unverified servers; and the play-only command
  profile.

### Modified Capabilities
- `extension-api`: extensions can read whether a player is trusted.

## Impact

- `protocol/Config.java`: `trustedPlayers` and `agentCommands`, saved
  when the list changes.
- The chat hooks of both loaders and the vanilla targets: the sender
  passed with player chat, so `Observations` and `Events` can withhold
  its text.
- A `/blockpilot` client command on each loader, and on the vanilla
  targets through the chat screen. It checks that no agent input reached
  the chat screen it came from.
- `Engine`/`CommandRegistry`: the play profile's refusals. `TalkCommands`
  and the chat-screen send path: slash commands for agents.
- `dev.blockpilot.api`: `BlockpilotApi.isTrusted(UUID)`.
- `tools/smoke_test.py` and the two-client runs: the second client's chat
  withheld until trusted. Runs that need its chat trust it in the first
  client's config before launch.
- `docs/safety.md`, the README, `docs/commands.md`, `docs/extending.md`
  and the CHANGELOG.

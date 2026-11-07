# Design

## Context

- Chat reaches Blockpilot through `ClientPlatform.hooks().onChatReceived(
  Component)`. On NeoForge that is `ClientChatReceivedEvent`, which knows
  the sender's UUID and whether the message is a system message. On
  Fabric it is `ClientReceiveMessageEvents.CHAT`, whose callback gets the
  sender's `GameProfile`, with `GAME` for system messages. The vanilla
  targets hook the chat listener. `BlockpilotClient` then logs the
  message (`Observations.message("chat", …)`) and `Events` broadcasts
  `! chat <text>`. The sender isn't passed on today.
- `Injection` sends Blockpilot's own keys, clicks and characters through
  vanilla's handlers, and `Injection.active()` is true while one runs. The
  human-input detection already tells agent input from a person's this
  way.
- `cmd` calls `connection.sendCommand`, and `chat` calls `sendChat`. The
  chat screen sends a typed `/command` through `sendCommand` too.
- `protocol/Config` loads `config/blockpilot/config.json` and writes it
  with defaults when it is missing.
- Scripts are parsed before any step runs (`Engine` and `Parser`), which
  is where syntax errors already fail.

## Goals / Non-Goals

**Goals:**
- No player can steer an agent by chat unless a person trusted them.
- Neither the agent nor anyone talking to it can change who is trusted.
- An agent that takes requests from players can be held to playing.

**Non-Goals:**
- Text in the world (signs, books, item and entity names) and chat a
  server relays as system messages. The docs name them as untrusted.
- Limiting the agent outside the game. That is the agent's own permission
  setup, which `docs/safety.md` shows.
- Server-side protection. Blockpilot stays a client mod.

## Decisions

### D1: Trust by name, empty by default

`trustedPlayers` holds player names, matched ignoring case against the
sender's profile name. A name is what a person knows and types. On a
server that verifies accounts, a name belongs to one account at a time.
The agent's own player is always trusted, so its own messages and their
echoes pass.

Matching by UUID was the alternative. It would survive renames, but a
person can't type it, and on unverified servers UUIDs are derived from
names anyway (D3).

### D2: Withholding where chat comes in

The chat hook gains the sender: a UUID and name, or none for system
messages. `BlockpilotClient.onChatReceived` decides once: a player's
message from an untrusted sender becomes the text `withheld from=<name>`
before it reaches `Observations` and `Events`. So the log, `messages
match=` and the events never hold the text. The vanilla chat window is
left alone, since the person at the game still reads everything.

### D3: Unverified servers

When the integrated server runs the world, its `usesAuthentication()`
decides. On a remote server, the agent's own UUID is compared with the
offline UUID derived from its name. If they are equal, the server didn't
verify the account, so it doesn't verify anyone's. The result is checked
on joining and cached for the connection.

### D4: The `/blockpilot` command at the send hook, only from a person

A mixin on `ClientPacketListener.sendCommand` catches a command starting
with `blockpilot `, on every target, the vanilla ones included. It handles
the command locally and never sends it to the server. One path then serves
both loaders and the vanilla targets, without each loader's client-command
API, at the cost of tab completion. The replies go to the chat window as
local messages.

A command counts as typed by a person only if:
1. no Blockpilot-sent input reached the chat screen it came from since
   that screen opened (`Injection.active()` during a key or character,
   recorded per screen)
2. it wasn't sent while `Injection` was active, as an injected enter
3. it wasn't sent by `cmd`, which sets an agent-origin flag around its
   call

Anything else is dropped, and the agent's command fails with `err
not_allowed`. For `cmd`, that is direct. For a `key` that pressed enter,
the refusal is recorded and the action fails on its next tick. `chat`
refuses a text starting with `/blockpilot` up front.

### D5: The play profile at parse time

With `agentCommands: "play"`, `Engine` checks every parsed step,
including those nested in `if`, `wait` and `sample`, against the refused
set, and fails the script before any step runs. This is the same point as
a syntax error, so nothing half-runs. The set is a list in one place,
named by the command words as the registry knows them:
- `cmd`, `server`, `nbt`, `tick`, `gametest`, `snapshot`, `config`
- `record`, `perf start`, `load`
- `create world`, `delete world`, `join`, `leave`, `quit`

Extension commands are allowed. The person who installed the mod chose
them.

A value of `agentCommands` other than `all`, such as a typo, counts as
`play`: a safety setting that can't be read fails closed. The config keys
are camelCase, `trustedPlayers` and `agentCommands`, like the file's other
keys.

Under `play`, the same `sendCommand` hook drops any slash command sent
with agent origin (D4's rules), so typing `/op` into the chat screen
doesn't get around the refused `cmd`.

### D6: The API

`BlockpilotApi.isTrusted(UUID)` asks the client's trust state through a
supplier Blockpilot installs at load, like the event sink. Without
Blockpilot running, it answers false. It joins 2.1.0, which isn't released
yet, and the 2.0.0 fixture test keeps 2.0 integrations honest.

## Risks / Trade-offs

- [Agents on servers no longer see other players' chat by default] →
  Intended, and marked BREAKING in the CHANGELOG. One `/blockpilot trust`
  per player restores it. Single-player and tests are unaffected, and the
  two-client runs trust the second client in the first's config.
- [Servers with chat plugins send player chat as system messages, which
  pass] → Documented in `docs/safety.md`. Trust protects chat that
  arrives as player chat, which covers vanilla and LAN worlds and modded
  servers without such plugins.
- [A person types the command into a chat screen an agent typed in
  before] → Refused, since agent input reached that screen. Reopening
  chat clears it.
- [Someone with access to the PC's files edits the config] → Out of reach
  for a game mod. The docs say whoever runs the agent controls its file
  access.

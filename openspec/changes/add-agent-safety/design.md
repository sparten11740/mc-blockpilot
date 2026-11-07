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
  chat screen sends a typed `/command` through `sendCommand` too. A
  command clicked in a chat message goes out through `sendUnsignedCommand`
  on 1.21.1 and `sendUnattendedCommand` from 26.1 on, which send the
  packet themselves.
- `protocol/Config` loads `config/blockpilot/config.json` at start and
  writes it back with the defaults of missing keys.
- Scripts are parsed before any step runs (`Engine` and `Parser`), which
  is where syntax errors already fail.

## Goals / Non-Goals

**Goals:**
- No player can steer an agent by chat unless a person trusted them.
- Neither the agent nor anyone talking to it can change who is trusted:
  that is the config file, and only the file.
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

Development runs log in with offline accounts, so every two-client run is
unverified. `-Pblockpilot.trustUnverified` makes the dev run pass the
system property `blockpilot.trustUnverified`, which applies the list on
unverified servers too. It counts only where the platform says this is a
development environment (NeoForge's `FMLEnvironment.production` false,
Fabric's `isDevelopmentEnvironment()`); the vanilla targets and production
installs ignore it, so a player's launcher can't switch it on, and agents
have no command for it. `trust` reports `trust_unverified` while it is
switched on, which also shows that a dev run got the property.

### D4: Trust only in the file

Trust is set in the config file and nowhere else. An in-game command for a
person (`/blockpilot trust`) was designed and dropped: telling a person's
typing from an agent's is one more thing that can go wrong, and the file
is out of reach of anything said in the game. So Blockpilot has no command
that changes `trustedPlayers` or `agentCommands`, `trust` takes no
arguments, and Blockpilot writes the file only at start, keeping what it
read and adding missing keys' defaults.

### D5: The play profile at parse time

With `agentCommands: "play"`, `Engine` checks every parsed step,
including those nested in `if`, `wait` and `sample`, against the refused
set, and fails the script before any step runs. This is the same point as
a syntax error, so nothing half-runs. The set is a list in one place,
named by the command words as the registry knows them:
- `cmd`, `server`, `nbt`, `tick`, `gametest`, `snapshot`, `config`
- `record` in every form; `perf` in every form but `perf status`, as a
  timed capture and `perf start`/`stop` write to disk like `record`; `load`
- `create world`, `delete world`, `join`, `leave`, `quit`

Extension commands are allowed. The person who installed the mod chose
them.

A value of `agentCommands` other than `all`, such as a typo, counts as
`play`: a safety setting that can't be read fails closed. The config keys
are camelCase, `trustedPlayers` and `agentCommands`, like the file's other
keys.

Under `play`, the send hooks drop any slash command sent with agent
origin (D8), so typing `/op` into the chat screen doesn't get around the
refused `cmd`.

### D6: The API

`BlockpilotApi.isTrusted(UUID)` asks the client's trust state through a
supplier Blockpilot installs at load, like the event sink. Without
Blockpilot running, it answers false. It joins 2.1.0, which isn't released
yet, and the 2.0.0 fixture test keeps 2.0 integrations honest.

### D7: Settings reloaded from the file

About once a second the client thread reads the config file and compares
its text with the last it applied. Comparing the text, not the
modification time, also catches two saves within the filesystem's time
granularity, as a test that writes a setting and restores it at once
makes them. When it changed, `trustedPlayers` and `agentCommands` are
applied to `Trust` and to the engine's profile, which `Engine` holds in a
volatile field read when a script is submitted. Other settings, such as
the port, still need a restart. A file that doesn't parse leaves the last
good settings in force and logs one error for that text of the file. The
reload reads without writing, so it never changes what a person wrote. A
file watcher was the alternative; reading a few hundred bytes once a
second costs nothing and needs no thread.

### D8: Slash commands for play-only agents at the send hooks

A mixin on `ClientPacketListener` hooks every way a slash command leaves
the client: `sendCommand`, and the click-event path (`sendUnsignedCommand`
on 1.21.1, `sendUnattendedCommand` from 26.1 on, chosen by a version
condition). Under `play` it drops a command sent with agent origin, and
the step that sent it fails with `err not_allowed cmd`. A command has agent
origin when:
1. it was sent while `Injection` was active, as an injected enter or click
2. Blockpilot-sent input reached the chat screen it came from since that
   screen opened (recorded per screen by `Injection`)

`cmd` is refused at parse time and never gets this far. The hook can't
throw through vanilla code, so it raises the refusal on `Refusal`, which
`ScriptJob` takes after the step's start and after each of its ticks; it
then cancels the step and fails it. A refusal left pending is dropped as
the next step starts.

## Risks / Trade-offs

- [Agents on servers no longer see other players' chat by default] →
  Intended, and marked BREAKING in the CHANGELOG. A name in the config
  file restores it, within a second. Single-player and tests are
  unaffected.
- [Servers with chat plugins send player chat as system messages, which
  pass] → Documented in `docs/safety.md`. Trust protects chat that
  arrives as player chat, which covers vanilla and LAN worlds and modded
  servers without such plugins.
- [Under `play`, a person's slash command typed into a chat screen an
  agent typed in before is dropped] → Agent input reached that screen, so
  it can't be told apart. Reopening chat clears it.
- [A broken config file at start] → The defaults apply (nobody trusted,
  `all`) and the error is logged; a broken file later keeps the last good
  settings (D7).
- [Someone with access to the PC's files edits the config] → Out of reach
  for a game mod. The docs say whoever runs the agent controls its file
  access.

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

### D2: Withholding before the chat window

The chat hook gains the sender: a UUID, or none for system messages, and
judges the line before the chat window adds it. A player's line from an
untrusted sender becomes the text `withheld from=<name>` there, so the
window, its `[CHAT]` log line, screenshots and recorded frames, the message
log, `messages match=` and the events all hold only the stand-in:
- NeoForge: a listener at the highest priority sets the event's message,
  so later listeners and the window get the stand-in.
- Fabric: its message API can't change a chat line, so `ALLOW_CHAT`
  cancels an untrusted one and adds the stand-in to the chat window
  itself.
- Vanilla targets: the `addPlayerMessage` mixin swaps the line.

When the agent hosts the world, the integrated server logs each player's
line too. A mixin on `PlayerList`'s chat broadcast notes the sender and
swaps the logged content the same way. Dropping the line from the agent's
own screen was weighed against people's needs: the agent's game belongs to
the agent, and the people it plays with read chat on their own games.

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
good settings in force and logs one error for that text of the file;
anything thrown while applying it is caught the same way. The
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

### D9: Gameplay screens and links for play-only agents

The engine refuses commands, but a play-only agent could still leave
through the game's own screens: the pause menu's buttons, the title
screen's server and world lists, the options, and the confirm screen of a
link. `Injection`, through which every key, character, click, move and
scroll of Blockpilot's reaches the game, asks a screen gate before it sends
anything. Under `play` the gate lets input reach only the world (no
screen), `AbstractContainerScreen` (inventories, chests, mod machines),
`ChatScreen`, sign editing and the book screens. On any other screen the
input isn't sent, and the step that sent it fails through `Refusal` with
`err not_allowed screen`. It is a list of what is allowed, so a screen
nobody thought of is refused. The escape key always passes, since it only
closes screens, and so do releases, so that no key stays held down. The
death screen isn't on the list, as its title-screen button leaves the
world; `respawn` respawns without it.

Links are refused where vanilla acts on a clicked text's click event:
`Screen.handleComponentClicked` on 1.21.1, `Screen.defaultHandleClickEvent`
from 26.1 on. Under `play`, a click Blockpilot made on one that opens a
web address or a file, or copies to the clipboard, isn't acted on and fails
with `err not_allowed link`, before the confirm screen and also with the
chat-links prompt off. A person's click goes through.

An extension command that runs on the integrated server reaches beyond the
player as `server` does, Blockpilot's own Sable commands included, so the
play profile refuses it as `<namespace>:<name>`.

On the integrated server, `/msg` and `/teammsg` send their line straight to
the players and log nothing, so only the broadcast chat line needs the
server-side withholding of D2.

## Risks / Trade-offs

- [Agents on servers no longer see other players' chat by default] →
  Intended, and marked BREAKING in the CHANGELOG. A name in the config
  file restores it, within a second. Single-player and tests are
  unaffected.
- [Servers with chat plugins send player chat as system messages, which
  pass] → Documented in `docs/safety.md`. Trust protects chat that
  arrives as player chat, which covers vanilla and LAN worlds and modded
  servers without such plugins.
- [Mod screens that aren't container screens are refused under `play`]
  → Fails closed by design; the docs name the limit. A mod whose screen
  the agent needs can use container screens or add commands.
- [Under `play`, a person's slash command typed into a chat screen an
  agent typed in before is dropped] → Agent input reached that screen, so
  it can't be told apart. Reopening chat clears it.
- [A broken config file at start] → Fails closed: agents held to `play`,
  nobody trusted, the error logged and the file left alone. A broken file
  later keeps the last good settings (D7). Gson reads leniently, so a
  trailing comma in the list reads as a null name, which is dropped.
- [Trust is read on other threads, the integrated server's and an
  extension's] → Its state is two immutable snapshots, the names and what
  the client knows of the connection, each swapped in one write.
- [Someone with access to the PC's files edits the config] → Out of reach
  for a game mod. The docs say whoever runs the agent controls its file
  access.

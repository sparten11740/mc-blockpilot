# Playing with others safely

An agent that plays with people takes requests from what they say. On a server with others, anyone could try to tell
it what to do: slash commands with the agent player's rights, deleting worlds, closing the game. Blockpilot limits who
reaches the agent through the game and what the agent may do in it. What the agent may do on the computer is up to
the agent's own setup, the last section here.

## Trusted players

Player chat reaches agents with its text only from the players named in `trustedPlayers` in
`config/blockpilot/config.json`, matched by name ignoring case. The list is empty by default:

```json
{
  "trustedPlayers": ["Steve", "Alex"]
}
```

Everyone else's chat arrives as `withheld from=<name>`: in `! chat` events, in `messages` (where `match=` can't see the
text either), in the game's chat window and its log line, and so in screenshots, recordings and `log`. When the agent
hosts the world, the integrated server's log line for it is withheld too. The agent's own player always counts as
trusted, and system and mod messages pass as before. The people the agent plays with read every line on their own
games; only the agent's game shows the stand-in.

The list is set in the file only. A change to the file applies within a second, without a restart, and no command, in
the game or from an agent, changes it. `trust` shows whom it trusts and who of them is online. A file Blockpilot can't
read, such as one with a syntax error, leaves the last good settings in force and logs one error. A file that can't be
read when the game starts holds agents to `play` (below) and trusts nobody until it is fixed; Blockpilot leaves the
file as it is.

### Servers that don't check accounts

On a server in offline mode anyone can join under any name, so there nobody but the agent's own player counts as
trusted, and `trust` reports `unverified`. Blockpilot tells it by the agent's own player: the integrated server's
setting when the agent hosts, else whether the server gave the agent the UUID an offline server makes up from its
name. That check has a limit: an offline-mode server whose plugin hands out the players' real UUIDs looks verified,
and there a name proves nothing. Don't trust names on such a server.

Development runs log in with offline accounts, so their servers are unverified. Launched with
`-Pblockpilot.trustUnverified`, a development client applies the list there all the same, and `trust` reports
`trust_unverified`. Only a development environment honours it (NeoForge and Fabric say whether they are one); a
player's install and the vanilla targets ignore it.

## Other mods' commands

Commands that other mods register, and the events they send, reach agents only once their namespace is in
`allowedExtensions`, empty by default:

```json
{
  "allowedExtensions": ["mymod"]
}
```

Until then a call answers `err not_allowed <namespace>:<name>` before any step runs, the mod's events are dropped, and
`help` lists the namespace as not allowed. Blockpilot's own compat modules, such as its `sable:` commands, need no
entry; another mod's commands in the same namespace still do. Like the other settings, it is set in the file only and
applies within a second.

The allow-list decides which other mods' commands and events agents use. It is no sandbox against a malicious mod,
which runs with the game's full rights anyway, and an event isn't proven to come from the mod its namespace names:
install only mods you trust.

## Play-only agents

```json
{
  "agentCommands": "play"
}
```

With `play`, agents may only play. A script with any of these fails before any step with `err not_allowed <command>`:
`cmd`, `server`, `nbt`, `tick`, `gametest`, `snapshot`, `config`, `record`, `perf` (all but `perf status`), `load`,
`create world`, `delete world`, `join`, `leave` and `quit`. A slash command an agent types into the chat screen or
clicks in a chat message isn't sent either, and the command that sent it fails with `err not_allowed cmd`. Extension
commands still work, as the person who installed the mod chose them, except those that run on the integrated server,
which are refused like `server` as `err not_allowed <namespace>:<name>`.

Blockpilot's input (`click`, `type`, `key`, `mouse`, `scroll`, `drag`, `cursor`) reaches only gameplay screens: the
world itself, container screens (inventories, chests, mod machines), the chat screen, sign editing and books. On any
other screen, such as the pause menu, the title screen's server and world lists, the options or a confirm screen, it
fails with `err not_allowed screen`; the escape key always gets through, as it only closes screens. Mod screens that
aren't container screens are refused too. A click on a text that opens a web address or a file, or copies to the
clipboard, in chat or a book, fails with `err not_allowed link` before any confirm screen, also with the game's
chat-links prompt off.

The default, `all`, refuses nothing, since testing a mod needs these commands. A value Blockpilot doesn't know counts as
`play`.

## Text from players is untrusted input

Even a trusted player's words are a request the agent weighs, not an order: an agent that takes requests from players
should be set up so that the worst request it could follow does no harm. Some text from players reaches the agent
however the settings are:

- Chat a server relays as system messages, as some chat plugins do; Blockpilot can only withhold chat that arrives as a
  player's, with its sender.
- Signs, books, item names and entity names in the world.
- The name in `withheld from=<name>`, which that player chose.
- With the voice add-on, speech: whatever a trusted player's microphone picks up, other voices around them included.

## The agent's own setup

Blockpilot can only limit what happens through the game. Keeping the agent off the computer it runs on is the job of
the agent's own setup. For [Claude Code](https://code.claude.com/docs), every flag and key below is taken from its
documentation as of November 2026; check those pages when your version differs.

### Blockpilot's tools only

Give the agent a folder of its own and put this in `.claude/settings.json` there
([settings files](https://code.claude.com/docs/en/settings),
[permission rules](https://code.claude.com/docs/en/permissions)):

```json
{
  "$schema": "https://json.schemastore.org/claude-code-settings.json",
  "permissions": {
    "defaultMode": "dontAsk",
    "allow": [
      "mcp__blockpilot__run",
      "mcp__blockpilot__screenshot",
      "mcp__blockpilot__help"
    ],
    "deny": [
      "Bash", "PowerShell", "Monitor", "Read", "Glob", "Grep", "Edit", "Write", "NotebookEdit",
      "WebFetch", "WebSearch", "Agent", "Workflow", "SendMessage", "RemoteTrigger", "CronCreate"
    ],
    "disableBypassPermissionsMode": "disable",
    "disableAutoMode": "disable"
  }
}
```

- The `allow` rules name Blockpilot's three tools, `run`, `screenshot` and `help`, served under the name the server was
  added with (`blockpilot` in [connecting.md](connecting.md)). `dontAsk` mode denies every call that would otherwise
  ask, so nothing else runs without a prompt to approve it.
- The `deny` rules name the tools that run commands or programs (`Bash`, `PowerShell`, `Monitor`), read files (`Read`,
  `Glob`, `Grep`, which `dontAsk` would still allow inside the folder), change files (`Edit`, `Write`,
  `NotebookEdit`), reach the web (`WebFetch`, `WebSearch`), or hand work to other agents and sessions (`Agent`,
  `Workflow`, `SendMessage`, `RemoteTrigger`, `CronCreate`). A bare tool name removes the tool, and deny rules are
  checked before allow rules, so nothing can allow them back. The [tools
  reference](https://code.claude.com/docs/en/tools-reference) lists every tool.
- `disableBypassPermissionsMode` and `disableAutoMode` set to `"disable"` keep the session out of the modes that skip
  these checks.
- Allow rules in a project's `.claude/settings.json` apply once you have trusted the folder; deny rules apply at once.

### The same from the command line

Launching with flags gives the agent only Blockpilot, whatever the settings files say
([CLI reference](https://code.claude.com/docs/en/cli-reference)). Put the server in a file of its own,
`blockpilot-mcp.json` ([MCP](https://code.claude.com/docs/en/mcp)):

```json
{
  "mcpServers": {
    "blockpilot": {
      "command": "<game dir>/blockpilot/blockpilot",
      "args": ["--mcp"]
    }
  }
}
```

and start the agent with:

```sh
claude --tools "" --strict-mcp-config --mcp-config ./blockpilot-mcp.json --permission-mode dontAsk \
  --allowedTools "mcp__blockpilot__run" "mcp__blockpilot__screenshot" "mcp__blockpilot__help"
```

`--tools ""` turns off every built-in tool, `--strict-mcp-config` loads no MCP server but the ones in the file, and
`--allowedTools` lets Blockpilot's tools run without a prompt. On Windows the command is `cmd` with the arguments
`/c`, `<game dir>\blockpilot\blockpilot.cmd` and `--mcp`.

### Never skip permissions for it

Don't start an agent that takes requests from players with `--dangerously-skip-permissions` or `--permission-mode
bypassPermissions`. Claude Code's own documentation says to use that mode only "in isolated environments like
containers or VMs where Claude Code can't cause damage" ([permissions](https://code.claude.com/docs/en/permissions)).

### The strongest: a separate user or machine

For words from players, the safest agent runs where it can't reach anything else: as a separate operating system
user, or in a virtual machine or container, that can reach only Blockpilot's endpoint. The endpoint listens on the
loopback interface by default (`bind` in the config) and asks for the token in `config/blockpilot/token`, so that user
needs to read the token and run the launcher, and nothing else of yours. In a virtual machine or container, forward
only the endpoint's port to it.

[Back to the README](../README.md)

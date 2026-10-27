# Proposal

## Why

Agents that play-test a mod restart the game often, and both ways to reach
Blockpilot from outside break around that. The MCP server lives in the game,
so it disappears with every restart, and Claude Code marks an HTTP server that
was down when the session started as failed and doesn't retry it, so a session
that starts the game itself never gets the tools. The CLI survives restarts but
needs Python 3, which stock macOS and Windows don't have, and a Blockpilot
checkout. Every mod developer has the Java the game runs on, even when Gradle
toolchains leave no `java` on `PATH`.

## What Changes

- The command-line client moves into the mod jar, with the same options,
  output and exit codes. **BREAKING**: `tools/blockpilot.py` is removed;
  `tools/smoke_test.py` stays and calls the new client.
- At every start the game writes a launcher into its directory,
  `blockpilot/blockpilot` (`blockpilot.cmd` on Windows), that runs the client
  on the game's own Java with the jar the game loaded. Agents call it from any
  shell with no `java` on `PATH`, no Python and no checkout. It stays after the
  game exits, so it works across restarts.
- The client gets an MCP mode, `blockpilot --mcp`, that serves MCP over stdio.
  An MCP client launches it once per session, so its tools exist before the
  game does and outlive every restart: it answers `initialize` and `tools/list`
  itself, finds the running game through `endpoint.json` at each call, forwards
  tool calls to it, opens a new game session after a restart without the agent
  noticing, and answers with a clear error while no game runs.
- The game's HTTP MCP endpoint stays, for clients that can't launch a process.
  The README recommends the stdio mode and documents both.

## Capabilities

### New Capabilities

None.

### Modified Capabilities
- `dev-integration`: the CLI is part of the mod jar and runs through a
  launcher the game writes on its own Java, instead of a Python script.
- `agent-endpoint`: MCP is also served over stdio by the launcher, which keeps
  working across game restarts and while no game runs.

## Impact

- **Code:** a `dev.blockpilot.cli` package that uses only the JDK (client, MCP
  over stdio, JSON, launcher text), checked by a test; the MCP tool list and
  protocol versions move there so the game's MCP server and the stdio mode
  share them; a per-loader call for the paths the mod was loaded from; the game
  writes the launcher at start.
- **Tools:** `tools/blockpilot.py` is removed; `tools/smoke_test.py` calls the
  launcher.
- **Protocol:** none; the line protocol and the HTTP MCP endpoint keep their
  form.
- **Docs:** README sections on the shell client, MCP setup (stdio recommended,
  HTTP as the alternative) and use from other mods' dev environments.
- **Testing:** unit tests for the client, the JSON code, the launcher text and
  the stdio MCP mode without a game; in-game checks on both loaders and both
  Minecraft versions, including an MCP client started before the game and kept
  across a restart.

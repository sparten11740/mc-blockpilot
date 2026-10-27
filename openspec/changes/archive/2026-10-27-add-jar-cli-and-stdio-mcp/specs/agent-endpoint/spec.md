# Spec Delta

## ADDED Requirements

### Requirement: MCP over stdio
The launcher run as `blockpilot --mcp` SHALL serve MCP over stdio,
newline-delimited JSON-RPC, with the same tools as the HTTP endpoint. It SHALL
answer `initialize`, `ping` and `tools/list` itself, so an MCP client can start
it before the game and keep it across restarts, and it writes nothing but
protocol messages to stdout.

#### Scenario: Started before the game
- **WHEN** Claude Code is configured with `claude mcp add --transport stdio
  blockpilot -- <game dir>/blockpilot/blockpilot --mcp` and its session starts
  while no game runs
- **THEN** initialization succeeds, and `tools/list` returns `run`,
  `screenshot` and `help` as the HTTP endpoint does

#### Scenario: Screenshot through stdio
- **WHEN** an agent calls `screenshot` through the stdio server while the game
  runs
- **THEN** the result holds the frame as image content, as over HTTP

### Requirement: Stdio MCP follows the running game
Each tool call over stdio SHALL go to the game that `endpoint.json` in the
launcher's game directory names at that moment. When that is a game the stdio
server has no session with, for example after a restart, it SHALL open a
session there and run the call without an error to the agent. A call the game
may already have started SHALL NOT be sent again. Session settings, such as
`fmt json`, start from their defaults in a new session.

#### Scenario: Restart between calls
- **WHEN** an agent calls `run` with `quit`, starts the game again, and calls
  `run` with `status` once the game is up
- **THEN** the status arrives as the result, with no reconnect step by the
  agent or the MCP client

#### Scenario: Game closes during a call
- **WHEN** the game exits while a `run` call is still executing
- **THEN** the result has `isError: true` and says the game closed during the
  call, and the call is not repeated

### Requirement: Stdio MCP without a game
While no game is reachable in its game directory, a tool call over stdio SHALL
return a result with `isError: true` whose text says that no game runs in that
directory. The stdio server keeps running, and the next call after the game is
up succeeds.

#### Scenario: Call before launch
- **WHEN** an agent calls `run` with `status` before the game has started
- **THEN** the result has `isError: true` and names the game directory, and
  the same call succeeds once the game's endpoint is up

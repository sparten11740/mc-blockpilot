# Spec Delta

## Purpose

The local network endpoint that agents and scripts use to reach the running
game. It serves both MCP and a raw line protocol on one port, protected by a
token.

## ADDED Requirements

### Requirement: Loopback-only listener on a configurable port
While the client runs, the mod SHALL listen on `127.0.0.1` on a configurable
port (default `25599`). It SHALL NOT bind to non-loopback interfaces unless the
user explicitly sets a bind address in the config.

#### Scenario: Default binding
- **WHEN** the client starts with default config
- **THEN** a TCP listener is open on `127.0.0.1:25599`
- **AND** connections to the machine's LAN address on that port are refused

#### Scenario: Port in use
- **WHEN** the configured port is already in use (another game instance)
- **THEN** the endpoint binds the next free port, up to the configured number of
  tries, and `blockpilot/endpoint.json` records the port it got

#### Scenario: No free port
- **WHEN** none of the ports it tries is free
- **THEN** the mod logs the failure and the game keeps running without the
  endpoint

### Requirement: Token authentication
The mod SHALL generate a random token on first start, store it in its config
directory, and reject any request that doesn't present it. MCP requests carry it
as `Authorization: Bearer <token>`. Line-protocol connections send `auth
<token>` as their first line.

#### Scenario: MCP request without token
- **WHEN** an HTTP request to `/mcp` lacks a valid bearer token
- **THEN** the endpoint responds `401` and runs nothing

#### Scenario: Line connection authenticates
- **WHEN** a TCP client sends `auth <valid token>` as its first line
- **THEN** the endpoint replies `ok blockpilot <mod version> api<api version>
  mc<minecraft version> <loader>` and accepts commands

#### Scenario: Line connection sends a command before auth
- **WHEN** the first line is not a valid `auth` line
- **THEN** the endpoint replies `err auth` and closes the connection

### Requirement: Protocol detection on a single port
The endpoint SHALL tell HTTP from the line protocol by the first bytes of each
connection, and serve both on the same port.

#### Scenario: HTTP client
- **WHEN** a connection starts with an HTTP request line
- **THEN** it is handled as HTTP

#### Scenario: Line client
- **WHEN** a connection starts with any other text line
- **THEN** it is handled as the line protocol

### Requirement: MCP Streamable HTTP server
The endpoint SHALL implement the MCP Streamable HTTP transport at `/mcp`:
JSON-RPC `initialize`, `notifications/initialized`, `ping`, `tools/list` and
`tools/call` via POST with JSON responses, and an `Mcp-Session-Id` header that
scopes per-session state.

#### Scenario: Standard MCP client connects
- **WHEN** an MCP client configured with `http://127.0.0.1:25599/mcp` and the
  bearer token initializes
- **THEN** initialization succeeds, a session id is returned, and `tools/list`
  returns the tool set

#### Scenario: Unknown session
- **WHEN** a request carries a session id the server does not know
- **THEN** the server responds `404` so the client re-initializes

### Requirement: Minimal MCP tool set
The MCP tool set SHALL be `run` (executes a command-language script),
`screenshot` (returns an image), and `help` (returns the command reference for a
topic). Tool descriptions SHALL together stay under 400 tokens, with the full
command reference available only through `help`.

#### Scenario: Listing tools
- **WHEN** an agent calls `tools/list`
- **THEN** exactly `run`, `screenshot` and `help` are returned
- **AND** the `run` description contains a cheat sheet of the most common
  commands

#### Scenario: Command error is a tool result, not a protocol error
- **WHEN** a `run` script fails on an unknown block name
- **THEN** the tool result has `isError: true` and the command-language error
  line as text content

### Requirement: Line protocol framing
On the line protocol, each request SHALL be one line, optionally prefixed with
`#<id> `. In text mode, the reply SHALL be one or more lines ending with a line
holding only `.`, and if the request had an id the reply's first line repeats
it. In JSON mode, each reply SHALL be exactly one line of JSON with the id as an
`id` field, and pushed events are JSON lines too.

#### Scenario: Pipelined requests
- **WHEN** a client sends `#1 status` and `#2 inv` without waiting
- **THEN** it receives two `.`-terminated replies, tagged `#1` and `#2`, in
  request order

#### Scenario: JSON line framing
- **WHEN** a client in JSON mode sends `#3 status`
- **THEN** it receives a single line `{"id":"3","results":[…],"events":[…]}` and
  no `.` line

#### Scenario: Pushed events
- **WHEN** a line client has sent `sub on` and the player receives a chat
  message
- **THEN** a line starting with `!` describing the event is written to the
  connection at once, outside any reply block

### Requirement: Game state is accessed on the client thread
Commands SHALL read and change game state only on the game's client thread.
Network I/O SHALL never block that thread, and a command waiting on the game
SHALL never block the network layer.

#### Scenario: Slow agent does not stall rendering
- **WHEN** an agent has an open connection but sends nothing
- **THEN** the client frame rate is unaffected

### Requirement: Single action queue across sessions
Every session SHALL be able to run read-only commands at any time. Commands that
act on the game SHALL run through one shared FIFO action queue. Any session's
`stop` cancels the running and queued actions.

#### Scenario: Two sessions issue actions
- **WHEN** session A runs `goto 100,64,100` and session B then runs `jump`
- **THEN** B's `jump` starts only after A's `goto` finishes or is cancelled

### Requirement: Low per-request overhead
For commands that need no game ticks to finish (e.g. `status`), the endpoint
SHALL answer within 2 game ticks of receiving the request, apart from time spent
on the network.

#### Scenario: Status latency
- **WHEN** an authenticated line client sends `status` while in a world at 20
  TPS
- **THEN** the reply arrives within 100 ms

# Design

## Context

The CLI is `tools/blockpilot.py`: it reads `<gameDir>/blockpilot/endpoint.json`
(port, pid, token file), speaks the line protocol, and turns the reply into the
exit code. `tools/smoke_test.py` runs it as a subprocess. MCP is served only by
the game: `McpServer` answers `/mcp` on the endpoint's port. It is built on
Gson, as is the rest of the protocol package, and Gson, netty and slf4j come
from the game at runtime; none of them is in the mod jar. A plain `java -cp
<jar>` therefore has the JDK and Blockpilot's own classes, nothing else.

The jars are compiled for the Java of their Minecraft version (21 for 1.21.1,
25 for 26.1), and in dev runs the loaders load Blockpilot from build folders
rather than a jar. `ClientPlatform.Services` is where per-loader facts such as
the game directory come from. See proposal.md for the motivation.

## Goals / Non-Goals

**Goals:**
- One client implementation for the shell and for MCP over stdio, in every
  jar, using only the JDK.
- The launcher runs exactly the Java and the Blockpilot build the game last
  ran with, so the class file version always fits.
- An MCP client that starts before the game, or stays across restarts, needs
  no reconnect.

**Non-Goals:**
- The stdio server doesn't start, stop or wait for the game. Starting it stays
  the agent's job; the CLI's `--wait-ready` covers waiting from a shell.
- No server-sent events over stdio: pushed game events stay on the line
  protocol (`sub on`), as over HTTP.
- No standalone CLI artifact and no Python fallback.
- No forwarding of `notifications/cancelled`; agents cancel with `run stop`.

## Decisions

### D1. A JDK-only `dev.blockpilot.cli` package in the shared tree

The client, the stdio MCP server, a small JSON reader and writer, the shared
MCP definitions (D5) and the launcher text (D2) live in `dev.blockpilot.cli`
under `src/main/java`, so every target's jar carries them. Its classes import
only `java.*` and `dev.blockpilot.cli.*`; `ArchitectureTest`, which already
holds the import rules of the api and protocol layers, fails on any other
import and on any mention of the game's libraries or Blockpilot's other
packages. Game code may use the package, never the other way round. The entry
point is `dev.blockpilot.cli.Main`.

*Alternatives:* a separate client jar (another artifact to publish and to find
in a dev environment); a source set compiled with a low `--release` so any
system Java runs it (not needed once the launcher uses the game's Java, and
extra build setup in the Stonecutter tree).

### D2. The launcher

At client start, independent of whether the endpoint could bind, the game
writes `<gameDir>/blockpilot/blockpilot` (a POSIX `sh` script, owner-writable,
executable for all) or `blockpilot.cmd` on Windows. It runs:

- the Java at `java.home` (`bin/java`, or `bin\java.exe` on Windows). Not the
  process's own command: on Windows the game often runs `javaw.exe`, which has
  no console.
- the classpath the loader loaded Blockpilot from, from a new
  `ClientPlatform.Services.modPaths()`: the mod file's paths on NeoForge; on
  Fabric the code source of Blockpilot's classes plus the mod container's
  origin, since a dev run's origin is only the resources folder holding
  `fabric.mod.json`. In dev runs these are the build folders, which work as
  classpath entries too. On 1.21.1 NeoForge the loader names only the primary
  path, which in a dev run is the classes folder.
- `-Dblockpilot.version=<the version the game reports>`, for D5.
- `dev.blockpilot.cli.Main --game-dir <absolute game dir>`, then the caller's
  arguments, so a later `--game-dir` wins.

Before starting Java the script checks that Java and every classpath entry
exist and otherwise exits 2 with "start the game once to update this
launcher". The text is built by a pure `Launcher` class in the cli package
(POSIX single quotes with `'\''` and `exec`; `.cmd` double quotes, `%*` and
`& exit /b` on Java's line, because cmd reads a running script by offset and
the game may rewrite it meanwhile), unit-tested with spaces and quotes in
paths. It is written to a temporary file and moved into place, so a
call never sees half a script, and it is left in place when the game exits.
When `modPaths()` yields nothing that exists, the game logs a warning and
writes no launcher.

### D3. The client is a port of `blockpilot.py`

Same options (`--game-dir`, default `run` when started without the launcher;
`--json`; `--timeout`; `--wait-ready`; `--wait-world`), the same stdout, the
same stderr summary line and the same exit codes. Process checks use
`ProcessHandle`: `of(pid)` for the stale-file test and `onExit()` for the wait
after `quit`, which also work on Windows, where the Python script skipped the
stale check. Arguments are parsed by hand; `--help` prints the usage.

### D4. JSON without a library

A small reader (objects as ordered maps, arrays as lists, numbers, strings,
booleans, null) and a compact writer cover `endpoint.json`, the results of a
`--json` reply and every MCP message.

*Alternative:* shading Gson (about 250 KB per jar, a relocated copy next to
the game's own, and more build setup for both loaders).

### D5. One source for the MCP tool list

`McpTools` in the cli package holds the tool definitions (names, descriptions
including the `run` cheat sheet, input schemas), the supported protocol
versions and the instructions text, as JSON built with D4's writer.
`McpServer` parses it once into Gson, and the stdio server sends it as it is,
so both transports list the same tools. `Help.cheatSheet()` reads from it. The
`serverInfo` version is the `blockpilot.version` property the launcher sets
from what the game reports. A resource in the jar would do for jars, but a
1.21.1 NeoForge dev run puts only the classes folder on the launcher's
classpath, not the resources.

### D6. The stdio server relays tool calls to the game's `/mcp`

`blockpilot --mcp` reads one JSON-RPC message per stdin line and writes one per
stdout line, through a single synchronized writer; everything else, including
its own log lines, goes to stderr.

- `initialize`, `ping` and `tools/list` are answered from D5. Notifications
  are dropped. A batch (JSON array) is answered with `-32600`, as batches left
  MCP in 2025-06-18.
- Each `tools/call` runs on its own thread, so a long `run` doesn't hold back
  a `ping`. It reads `endpoint.json` again, checks the pid, and when that game
  is not the one the current session belongs to (new pid or port) it opens a
  session there: `initialize` with the protocol version agreed with the
  client, then `notifications/initialized`. The call is then posted with the
  client's id, and the game's JSON body becomes the stdout line. Raw newlines
  are removed first, which is safe because JSON strings can't hold them.
- Retry rule: a `404` (unknown session) or a refused connection means the game
  never ran the call, so the server reopens the session and sends it once
  more. A connection that breaks after the request went out may have run it,
  so the result is `isError: true` saying the game closed during the call, and
  it is not sent again.
- With no live game, the result is `isError: true` naming the game directory
  (see the agent-endpoint delta).
- HTTP goes through `java.net.http.HttpClient` pinned to HTTP/1.1, since the
  game's codec serves only that, with the bearer token from the token file,
  no `Origin` header and no request timeout (the MCP client's own timeout
  applies). On stdin EOF the server sends `DELETE` for its game session, best
  effort, and exits.

*Alternative:* speaking the line protocol and building MCP results in the
proxy. That would duplicate `McpServer`'s results and screenshot encoding;
relaying keeps one implementation of the tools, in the game.

### D7. The HTTP endpoint stays

`/mcp` keeps serving clients that can't launch a process. The README lists, in
order: the shell client through the launcher, MCP over stdio (recommended,
with `claude mcp add --transport stdio blockpilot -- <abs game
dir>/blockpilot/blockpilot --mcp`, and `cmd /c` before the `.cmd` on Windows),
MCP over HTTP, and the line protocol.

### D8. Tools

`tools/blockpilot.py` is deleted. `tools/smoke_test.py` runs the launcher in
the game directory it is given (`blockpilot.cmd` on Windows) instead of the
script, and keeps its checks; Python remains only for Blockpilot's own smoke
test.

## Risks / Trade-offs

- [NeoForge loads mods through its own file system, and dev runs load
  folders, so a path may not be a plain jar] → `modPaths()` goes through each
  loader's API, and the tasks check the launcher in dev runs and with a
  production jar on both loaders; with no usable path the game warns and
  writes no launcher.
- [The launcher exists only after the game started once in that directory] →
  the README says to start the game once before adding the MCP server, and a
  launcher whose jar is gone says the same.
- [Windows is not tried here] → the `.cmd` text is unit-tested, the README
  notes `cmd /c`, and a Windows check is left to the first user there.
- [A stray `println` on stdout would corrupt the MCP stream] → one writer owns
  stdout in MCP mode, and a unit test drives the server with no game and
  checks that every stdout line is a JSON-RPC message.
- [Each shell call starts a JVM, about 0.2 to 0.5 s] → small next to game
  actions; MCP mode is one long-lived JVM.
- [Session settings such as `fmt json` reset after a restart] → stated in the
  spec; the shell client sets them per call anyway.

## Migration Plan

Scripts that ran `python3 tools/blockpilot.py --game-dir <dir> …` run
`<dir>/blockpilot/blockpilot …` with the same arguments. HTTP MCP setups keep
working; switching to stdio is optional. The line protocol and the HTTP
endpoint don't change, so going back means an older jar plus the Python script
from git history.

# Tasks

## 1. Client in the jar (dev-integration)

- [x] 1.1 Add the `dev.blockpilot.cli` package with the JSON reader and writer (D4) and its import rule in `ArchitectureTest` (D1); verify the JSON round-trip and malformed-input unit tests pass, and that the import rule fails with a planted `com.google.gson` import in the package and passes without it
- [x] 1.2 Port `tools/blockpilot.py` to `dev.blockpilot.cli.Main` (D3) with unit tests for the summary line, text and JSON result parsing and the `quit` pid; verify on the 1.21.1 NeoForge dev client that `java -cp <its classes and resources> dev.blockpilot.cli.Main --game-dir run` matches the Python script's stdout, stderr and exit code for `status; inv`, a failing command, a syntax error, `--json`, `--wait-ready 180 --wait-world` right after `runClient`, a killed game (stale file, exit 2) and `quit` (exit 0 once the process is gone)

## 2. Launcher (dev-integration)

- [ ] 2.1 Add `ClientPlatform.Services.modPaths()` for NeoForge and Fabric and the pure `Launcher` text builder for `sh` and `.cmd` (D2), with unit tests for paths holding spaces and quotes, the preset `--game-dir` before the caller's arguments and the missing-classpath check; verify the tests pass on both loaders' nodes
- [ ] 2.2 Write the launcher at every client start, atomically, kept after exit, and with a warning instead when no mod path exists (D2); verify on the 1.21.1 and 26.1 NeoForge and the 26.1 Fabric dev clients that `run/blockpilot/blockpilot "status"` works from `env PATH=/usr/bin:/bin`, exits 2 as unreachable after the game exited, runs a rebuilt build after the next start, and exits 2 with the start-once message when a classpath entry it names is gone
- [ ] 2.3 Switch `tools/smoke_test.py` to the launcher, delete `tools/blockpilot.py`, and rewrite the README's shell-client and dev-environment parts for the launcher (D8); verify the smoke test passes in text and JSON mode on the 1.21.1 and 26.1 NeoForge dev clients, no file in the repository still names `blockpilot.py`, and every shell command the README shows runs as written

## 3. MCP over stdio (agent-endpoint)

- [ ] 3.1 Move the MCP tool definitions, protocol versions and instructions into `McpTools`, build `McpServer`'s answers and `Help.cheatSheet()` from it, and add the version resource the build writes (D5); verify `McpServerTest` passes and `tools/list` over HTTP returns the same JSON as before the move on both loaders' nodes
- [ ] 3.2 Add `--mcp` with local `initialize`, `ping` and `tools/list`, the no-game result and batch rejection (D6), with a unit test that drives stdin and stdout with no game running; verify initialize succeeds, `tools/list` equals `McpTools`, a `tools/call` returns `isError` naming the game directory, and every stdout line is a JSON-RPC message
- [ ] 3.3 Relay `tools/call` to the game's `/mcp` with the session, restart and retry rules of D6, with unit tests against a stub HTTP server for a `404`, a refused connection and a connection that breaks after the request; verify the tests pass, and on the 1.21.1 NeoForge dev client that Claude Code configured through `claude mcp add --transport stdio` and started before the game lists the tools, runs `status` once the game is up, still runs it after `quit` and a restart with no `/mcp` reconnect, returns a `screenshot` as an image, and reports a call cut off by closing the game as `isError` without running it twice
- [ ] 3.4 Rewrite the README's MCP part: stdio first with the `claude mcp add` line, `cmd /c` on Windows and "start the game once", then HTTP as the alternative (D7); verify the documented commands work as written on the 1.21.1 NeoForge dev client

## 4. Integration checks

- [ ] 4.1 Build all four jars; verify each holds `dev/blockpilot/cli`, `ArchitectureTest` passes on every node, `java -cp <jar> dev.blockpilot.cli.Main --help` runs on Java 21 for the 1.21.1 jars and on Java 25 for the 26.1 jars, and the smoke test passes on the 1.21.1 and 26.1 Fabric dev clients too
- [ ] 4.2 Load Blockpilot as a jar in a fresh NeoForge 1.21.1 MDK project and a fresh Fabric 1.21.1 example-mod project through mavenLocal, as the README describes; verify in both that the launcher appears in `run/blockpilot`, runs `status` with no `java` on `PATH`, and serves `tools/list` and a `run` call over stdio

## Workflow follow-up

- Archive the change once the tasks are done.

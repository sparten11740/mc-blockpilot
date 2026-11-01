# Connecting

Put the jar for your loader into the client's `mods` folder (or a dev environment's `run/mods`, see
[dev-environment.md](dev-environment.md)) and start the game. Once it is up, `<game dir>/blockpilot/endpoint.json` names
the port (25599, or the next free one when several instances run) and the token file. The endpoint listens on
`127.0.0.1` only and needs the token.

## From a shell

At every start the game writes a launcher, `<game dir>/blockpilot/blockpilot` (`blockpilot.cmd` on Windows), that runs
Blockpilot's command-line client on the game's own Java with the jar it loaded, so it needs no `java` on the path, no
Python and no Blockpilot checkout. It stays after the game exits and works across restarts; it appears while the game
loads, so only the very first start in a directory has to wait for the file before using it.

```sh
run/blockpilot/blockpilot --wait-ready 300 --wait-world "status; look"
run/blockpilot/blockpilot --json "inv"
```

`--wait-ready <seconds>` waits for the game to come up, and `--wait-world` also for the player to be in a world. A crash
report the game writes meanwhile ends the wait at once, with exit code 2 and the report's path. A client that can't open
its window fails before any mod loads, so nothing reports that and the wait runs to its timeout; on macOS that is a
client started while the display sleeps ("Failed to locate a primary monitor"), which `caffeinate -u -t 300` before the
start avoids by waking the display. `--game-dir <dir>` talks to the game in another directory, and `--help` lists the
options.

Exit codes: 0 every command returned `ok`, 1 one returned `err`, 2 the game could not be reached (or the wait timed out,
or no reply came within `--timeout`, default 300, which cancels the script; stderr says after how many seconds). When
the game's client thread finishes no tick for 30 s outside Blockpilot's own commands, for example flooded with chat by a
command block, every waiting script answers `err stalled` with how long and how many chat lines arrived. When a step
failed or was skipped, one line on stderr counts them, names the failed steps (from 1) and says whether anything ran,
e.g. `blockpilot: 1 err (step 3), 2 skip (nothing after the error ran)`. After a `quit` the CLI returns only once the
game process has exited (within `--timeout`, else exit code 2), so a restart can follow straight away.

## Over MCP

The launcher also serves MCP over stdio, with three tools: `run` (a script of commands), `screenshot` and `help`. The
MCP client starts it once per session, so the tools are there before the game is and keep working through every restart
of the game, with no reconnect. Start the game once in that directory first, so the launcher exists, then from the
directory that holds `run`:

```sh
claude mcp add --transport stdio blockpilot -- "$PWD/run/blockpilot/blockpilot" --mcp
```

On Windows the command is `cmd /c <game dir>\blockpilot\blockpilot.cmd --mcp`. While no game runs, a tool call answers
with an error naming the game directory, and the first call after the game is up works. A restarted game gets a new
session, so per-session settings such as `fmt json` start from their defaults again.

The game also serves MCP itself, over Streamable HTTP, for clients that can't launch a process. That server goes away
with each restart of the game, and Claude Code doesn't retry an HTTP server that was down when its session started:

```sh
claude mcp add --transport http blockpilot http://127.0.0.1:25599/mcp --header "Authorization: Bearer $(cat run/config/blockpilot/token)"
```

The port is in `endpoint.json` (another game may already hold 25599). As a baseline, a Claude Code session that created
a survival world, mined four logs, collected the drops, crafted planks and a crafting table and placed it needed six
`run` calls and about 1,300 characters (roughly 500 tokens) of tool input and output, with no screenshots.

## Line protocol

Connect to the port (`nc 127.0.0.1 25599`), send `auth <token>`, then one script per line, optionally prefixed with
`#<id> `. Text replies end with a line holding only `.`; after `fmt json` each reply is one JSON line. `sub on` pushes
events as they happen.

## While an agent is connected

The window can stay in the background: while an agent is connected, and for a minute after its last connection closed
(the CLI opens one per call), the game does not pause and keeps handling input. Real keyboard or mouse input to the
focused window cancels running agent actions with `err interrupted`, which names the input (`input=key key=w`,
`input=button button=left`, `input=scroll`, or `input=move dx=<px> dy=<px>`); F8 toggles agent control off and on. A
line in the top-left corner shows when an agent is active and what it is doing.

[Back to the README](../README.md)

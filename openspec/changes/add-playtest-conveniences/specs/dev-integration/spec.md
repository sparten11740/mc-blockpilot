# Spec Delta

## ADDED Requirements

### Requirement: CLI waits for the game to exit
When a script's `quit` is answered `ok pid=<pid>`, the CLI SHALL wait until
that process has exited before it exits itself, within `--timeout`. It exits 0
once the process is gone, and 2 when it is still running at the timeout. A
restart loop then needs no process lookup.

#### Scenario: Restart loop
- **WHEN** an agent runs `python3 tools/blockpilot.py --game-dir run "quit"`
  and then starts the game again
- **THEN** the first command returns only after the old game process has
  exited, with exit code 0, so the new game can take the port and the
  readiness file

#### Scenario: Game hangs on the way out
- **WHEN** the game answers `quit` but is still running when `--timeout` runs
  out
- **THEN** the CLI exits with code 2 and says on stderr that the game is still
  running, with its pid

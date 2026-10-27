# dev-integration Specification

## Purpose

Makes Blockpilot easy to use from another mod's development environment. An
agent can launch the game, know when it is ready, land in a test world without
clicking, and send commands from a shell without reconfiguring MCP.

## Requirements

### Requirement: Endpoint readiness file
Once the endpoint is listening, Blockpilot SHALL write
`<gameDir>/blockpilot/endpoint.json` with the port, the process id, the mod and
API versions, and the token file path. It removes the file on clean shutdown. A
stale file from a dead process is recognizable by its process id.

#### Scenario: Agent waits for launch
- **WHEN** an agent starts `runClient` in the background and polls for
  `run/blockpilot/endpoint.json`
- **THEN** the file appears only once the endpoint accepts connections

### Requirement: Auto-join on launch
The JVM property `blockpilot.autojoin=<world>` SHALL make the client join that
singleplayer world as soon as the title screen (or a first-launch onboarding
screen) appears. With `blockpilot.autocreate=<flat|void|normal>` too, the world
is first created as by `create world` if it doesn't exist.

#### Scenario: First launch in a fresh run directory
- **WHEN** `runClient` starts with `-Dblockpilot.autojoin=aerolarge-test
  -Dblockpilot.autocreate=flat` in an empty run directory
- **THEN** the client skips past the onboarding screen, creates the flat test
  world, and joins it without any input

#### Scenario: Missing world without autocreate
- **WHEN** `blockpilot.autojoin` names a world that doesn't exist and
  `blockpilot.autocreate` is not set
- **THEN** the client stays on the title screen and logs a warning naming the
  world

### Requirement: Standalone CLI client
Blockpilot SHALL ship a command-line client in its mod jar, run through the
launcher, that needs nothing besides the game's Java. It reads `endpoint.json`
and the token from a game directory (the launcher's, or `--game-dir`), sends
one script per invocation, and prints the reply. Its exit code is 0 when every
command returned `ok`, 1 on any `err`, and 2 if the endpoint can't be reached.
A readiness file whose process is gone counts as unreachable.

#### Scenario: One-shot script
- **WHEN** an agent runs `run/blockpilot/blockpilot "status; inv"`
- **THEN** the text reply is printed to stdout, and the exit code is 0

#### Scenario: Stale readiness file
- **WHEN** the game was killed and its `endpoint.json` is still there
- **THEN** the CLI exits with code 2 instead of connecting to whatever now holds
  the port, on Windows as on the other systems

#### Scenario: JSON output
- **WHEN** an agent runs the CLI with `--json`
- **THEN** stdout is exactly one JSON object, the same as the line protocol's
  JSON reply

#### Scenario: Another game directory
- **WHEN** an agent runs a launcher with `--game-dir <other dir>`
- **THEN** the CLI talks to the game in that directory instead of the
  launcher's

### Requirement: CLI readiness wait
`--wait-ready <seconds>` SHALL make the CLI wait until the endpoint is
reachable. `--wait-world` additionally waits, within the same time, until the
player is in a world. Both run before the script, and exit with code 2 on
timeout.

#### Scenario: Launch then act
- **WHEN** an agent runs `run/blockpilot/blockpilot --wait-ready 180 "create
  world t flat"` right after starting `runClient`
- **THEN** the CLI blocks until the endpoint is reachable, then runs the script

#### Scenario: Launch with auto-join
- **WHEN** `runClient` uses `blockpilot.autojoin` and an agent runs the CLI with
  `--wait-ready 180 --wait-world "status"`
- **THEN** the CLI blocks until the player is in the world, then prints the
  in-world status

### Requirement: Jars available to other dev environments
The build SHALL publish each target's mod jar and the extension API
(`blockpilot-api`) to mavenLocal under `dev.blockpilot`. The README SHALL
document how to add Blockpilot as a runtime-only dependency in ModDevGradle and
Fabric Loom projects, with the auto-join properties.

#### Scenario: NeoForge dev env
- **WHEN** a NeoForge 1.21.1 mod project adds the documented runtime-only
  dependency and runs `runClient`
- **THEN** Blockpilot loads in that client next to the mod under development,
  and its endpoint starts

### Requirement: CLI waits for the game to exit
When a script's `quit` is answered `ok pid=<pid>`, the CLI SHALL wait until
that process has exited before it exits itself, within `--timeout`. It exits 0
once the process is gone, and 2 when it is still running at the timeout. A
restart loop then needs no process lookup.

#### Scenario: Restart loop
- **WHEN** an agent runs `run/blockpilot/blockpilot "quit"` and then starts the
  game again
- **THEN** the first command returns only after the old game process has
  exited, with exit code 0, so the new game can take the port and the
  readiness file

#### Scenario: Game hangs on the way out
- **WHEN** the game answers `quit` but is still running when `--timeout` runs
  out
- **THEN** the CLI exits with code 2 and says on stderr that the game is still
  running, with its pid

### Requirement: Launcher on the game's own Java
At every start Blockpilot SHALL write `<gameDir>/blockpilot/blockpilot`
(`blockpilot.cmd` on Windows), a launcher that runs the CLI on the Java the
game runs on, from the jar or folders the game loaded Blockpilot from, with
that game directory preset. It needs no `java` on `PATH`, stays after the game
exits, and is replaced at the next start.

#### Scenario: No Java on the path
- **WHEN** the game was started through Gradle with a toolchain JDK and an
  agent runs `run/blockpilot/blockpilot "status"` from a shell whose `PATH`
  has no `java`
- **THEN** the CLI runs and prints the status

#### Scenario: Blockpilot updated
- **WHEN** a newer Blockpilot jar replaces the old one and the game starts
- **THEN** the launcher runs the newer jar's CLI

#### Scenario: Jar gone
- **WHEN** the jar the launcher names was deleted after the game exited
- **THEN** the launcher exits with code 2 and says on stderr that the game has
  to start once to update it

### Requirement: Readiness wait stops at a crash report
While `--wait-ready` waits for the endpoint, a crash report that appears in
`<gameDir>/crash-reports` after the wait began SHALL end the wait at once with
exit code 2, naming the report on stderr. A client that fails before any mod
loads, as one that cannot open its window, writes nothing Blockpilot can see,
so the wait then runs to its timeout.

#### Scenario: Mod crashes during start
- **WHEN** an agent runs the CLI with `--wait-ready 300` while the game starts,
  and the game crashes and writes `crash-reports/crash-<time>-client.txt`
- **THEN** the CLI exits with code 2 within about a second, naming that file

#### Scenario: Old crash reports
- **WHEN** the crash-reports folder already holds reports from earlier runs
- **THEN** they do not end the wait

### Requirement: Readiness timeout says how long it waited
When `--wait-ready` runs out before the game is reachable, or before the
player is in a world with `--wait-world`, the CLI's stderr message SHALL say
how many seconds it waited, so an agent can tell a game that didn't start in
time from one that was never started.

#### Scenario: Game that never comes up
- **WHEN** an agent runs the CLI with `--wait-ready 300` and no game starts
- **THEN** the CLI exits with code 2 and its stderr message says it waited
  300 s

#### Scenario: Game left in the menu
- **WHEN** an agent runs the CLI with `--wait-ready 60 --wait-world` and the
  game stays in its title screen
- **THEN** the CLI exits with code 2 and its stderr message says it waited
  60 s for a world

### Requirement: Summary of failed steps
When steps of a script fail or are skipped, the CLI SHALL print one stderr
line counting them, and SHALL name the number of each failed step, counted
from 1 in the order of the reply's result lines.

#### Scenario: Error in the third step
- **WHEN** an agent runs the CLI with a five-step script whose third step
  fails
- **THEN** stderr says one step failed, step 3, and two were skipped

### Requirement: Reply timeout says what happened
When the game's reply to a script doesn't arrive within `--timeout`, the CLI
SHALL exit with code 2, and its stderr message SHALL say how many seconds it
waited, that the script was cancelled (closing the connection ends the
session, and with it the script), and that a longer script needs a larger
`--timeout`.

#### Scenario: Wait longer than the launcher's limit
- **WHEN** an agent runs the CLI without `--timeout` on a script whose `wait`
  has `timeout=540s`, and the wait doesn't end within 300 s
- **THEN** the CLI exits with code 2 after 300 s, and its stderr message says
  it waited 300 s, the script was cancelled, and to raise `--timeout`

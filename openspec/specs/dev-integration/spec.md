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
Blockpilot SHALL ship `tools/blockpilot.py`, a command-line client that needs
only Python 3 and its standard library. It reads `endpoint.json` and the token
from a game directory, sends one script per invocation, and prints the reply.
Its exit code is 0 when every command returned `ok`, 1 on any `err`, and 2 if
the endpoint can't be reached. A readiness file whose process is gone counts as
unreachable.

#### Scenario: One-shot script
- **WHEN** an agent runs `python3 tools/blockpilot.py --game-dir run "status;
  inv"`
- **THEN** the text reply is printed to stdout, and the exit code is 0

#### Scenario: Stale readiness file
- **WHEN** the game was killed and its `endpoint.json` is still there
- **THEN** the CLI exits with code 2 instead of connecting to whatever now holds
  the port

#### Scenario: JSON output
- **WHEN** an agent runs the CLI with `--json`
- **THEN** stdout is exactly one JSON object, the same as the line protocol's
  JSON reply

### Requirement: CLI readiness wait
`--wait-ready <seconds>` SHALL make the CLI wait until the endpoint is
reachable. `--wait-world` additionally waits, within the same time, until the
player is in a world. Both run before the script, and exit with code 2 on
timeout.

#### Scenario: Launch then act
- **WHEN** an agent runs `python3 tools/blockpilot.py --game-dir run
  --wait-ready 180 "create world t flat"` right after starting `runClient`
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

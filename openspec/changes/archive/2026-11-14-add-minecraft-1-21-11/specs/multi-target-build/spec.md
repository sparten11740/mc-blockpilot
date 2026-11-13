## MODIFIED Requirements

### Requirement: One jar per target from one source tree
The build SHALL produce one mod jar for each configured `<minecraft>-<loader>`
target from a single shared source tree. The targets are `1.20.1-fabric`,
`1.20.1-forge`, `1.21.1-neoforge`, `1.21.1-fabric`, `1.21.11-neoforge`,
`1.21.11-fabric`, `26.1-neoforge`, `26.1-fabric`, `26.1-vanilla`,
`26.2-neoforge`, `26.2-fabric`, `26.2-vanilla`, `26.3-neoforge`, `26.3-fabric`
and `26.3-vanilla`. The `-vanilla` targets run without a mod loader.

#### Scenario: Full build produces every target
- **WHEN** a developer runs the root build task
- **THEN** a jar is produced for each of the fifteen targets
- **AND** each jar's file name includes the mod version, the Minecraft version
  and the loader (e.g. `blockpilot-0.1.0+26.1.2-fabric.jar`,
  `blockpilot-0.1.0+26.3-vanilla.jar`)

#### Scenario: Jar loads only on its own loader
- **WHEN** the `1.21.1-fabric` jar is installed in a Fabric 1.21.1 client
- **THEN** the mod loads and opens its agent endpoint
- **AND** the jar contains no NeoForge-specific classes

#### Scenario: 1.20.1 jar on 1.20.1
- **WHEN** the `1.20.1-fabric` jar is installed in a Fabric 1.20.1 client with
  Fabric API, or the `1.20.1-forge` jar in a Forge 47 client, each on Java 17
- **THEN** the mod loads and opens its agent endpoint

#### Scenario: Forge jar on NeoForge 47.1
- **WHEN** the `1.20.1-forge` jar is installed in a NeoForge 47.1 client
- **THEN** the mod loads and opens its agent endpoint

#### Scenario: 1.21.11 jar on 1.21.11
- **WHEN** the `1.21.11-neoforge` jar is installed in a NeoForge 21.11
  client, or the `1.21.11-fabric` jar in a Fabric 1.21.11 client with Fabric
  API
- **THEN** the mod loads and opens its agent endpoint

#### Scenario: 26.1 jar on 26.1
- **WHEN** the `26.1-neoforge` jar is installed in a NeoForge 26.1.2 client,
  or the `26.1-fabric` jar in a Fabric 26.1.2 client
- **THEN** the mod loads and opens its agent endpoint

#### Scenario: 26.2 jar on 26.2
- **WHEN** the `26.2-neoforge` jar is installed in a NeoForge 26.2 client, or
  the `26.2-fabric` jar in a Fabric 26.2 client
- **THEN** the mod loads and opens its agent endpoint

#### Scenario: 26.3 jar on 26.3
- **WHEN** the `26.3-neoforge` jar is installed in a NeoForge 26.3 client, or
  the `26.3-fabric` jar in a Fabric 26.3 client
- **THEN** the mod loads and opens its agent endpoint

### Requirement: Identical agent-facing behavior across targets
For the same game situation, every target SHALL accept the same commands and
produce the same reply format. Version-specific differences show up only as
documented capability gaps that the target reports through `help`, or in key
names, which are the running game's and are documented in `docs/commands.md`.

#### Scenario: Same reply on both loaders
- **WHEN** the same `status` command is sent to the 1.21.1 NeoForge and 1.21.1
  Fabric builds in equivalent game states
- **THEN** both replies have identical structure and field order

#### Scenario: Same reply on both Minecraft versions
- **WHEN** the smoke test runs against a 1.21.1 jar and a 26.1 jar of the same
  loader
- **THEN** every step passes on both, and the JSON replies have the same
  fields

#### Scenario: Same reply on 1.20.1
- **WHEN** the smoke test runs against the `1.20.1-fabric` jar and the
  `1.21.1-fabric` jar, or the `1.20.1-forge` jar and the `1.21.1-neoforge` jar
- **THEN** every step passes on both, and the JSON replies have the same
  fields

#### Scenario: Same reply on 1.21.11
- **WHEN** the smoke test runs against a 1.21.11 jar and a 26.1 jar of the
  same loader
- **THEN** every step passes on both, and the JSON replies have the same
  fields

#### Scenario: Same reply on 26.2 and 26.3
- **WHEN** the smoke test runs against a 26.1 jar and a 26.2 or 26.3 jar of the
  same loader
- **THEN** every step passes on both, and the JSON replies have the same
  fields

#### Scenario: Same input names whatever the game numbers them
- **WHEN** an agent runs `mouse click left`, `mouse click right`,
  `mouse click middle` or `key left_shift+left` on a 26.2 and on a 26.3 jar, in
  equivalent game states
- **THEN** the same button or keys act on both, though 26.3 numbers them
  differently

#### Scenario: The cursor stays through a screen change
- **WHEN** an agent puts the cursor on a button and clicks it, on a 26.2 and on
  a 26.3 jar, and the click opens another screen
- **THEN** `screen` reports the cursor where the agent put it on both, though
  26.3 rereads the OS pointer on every new screen

#### Scenario: A background window leaves the pointer alone
- **WHEN** an agent opens or closes a screen in a window without focus, on a
  26.2 and on a 26.3 jar
- **THEN** the player's real pointer stays where it is on both

#### Scenario: Key names are the running game's
- **WHEN** an agent runs `key press f25` on a 26.3 jar, which has no such key
- **THEN** the result is `err syntax` with the closest key names, as for any
  unknown key

### Requirement: One script tests every target
One script SHALL test every configured target, or those it is told to, by
starting that target's client, running its assertions and closing the client. It
SHALL exit non-zero if any assertion fails, and name each failing target and
assertion.

#### Scenario: Every target passes
- **WHEN** a developer runs the script with no arguments and every assertion
  holds
- **THEN** it reports each of the fifteen targets as passed and exits with 0

#### Scenario: An assertion fails
- **WHEN** an assertion fails on `26.3-fabric`
- **THEN** the script still tests the remaining targets, reports `26.3-fabric`
  and that assertion's name, and exits non-zero

#### Scenario: A client doesn't come up
- **WHEN** a target's client stops at load or never opens its endpoint
- **THEN** that target is reported as failed with the reason, only that client
  is stopped, and the next target runs

#### Scenario: A client is lost during its assertions
- **WHEN** a target's client stops or stops answering while the script runs its
  assertions
- **THEN** the assertions it can't answer fail by name, that client is stopped
  if it is still there, and the next target runs

#### Scenario: A run is interrupted
- **WHEN** the script gets Ctrl-C, a stop request or a closed terminal, even
  twice
- **THEN** the client under way is stopped, the finished targets are reported,
  and it exits non-zero

#### Scenario: A client is already running
- **WHEN** a target's dev client is already running in its run folder
- **THEN** that target is reported as failed, naming the process, and that
  client and its endpoint are left alone

#### Scenario: Chosen targets only
- **WHEN** a developer names `26.2-neoforge` and `26.3-neoforge`
- **THEN** only those two clients start, and the result covers only them

#### Scenario: Reply fields differ between targets
- **WHEN** a query's JSON reply has different fields on two targets of the same
  loader, tested in this run or saved from an earlier one, where the vanilla
  targets count as Fabric, whose replies they share, and the Forge target as
  NeoForge
- **THEN** the script reports the query and both targets, and exits non-zero

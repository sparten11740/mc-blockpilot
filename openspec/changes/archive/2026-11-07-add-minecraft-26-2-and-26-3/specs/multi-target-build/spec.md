# Spec Delta

## MODIFIED Requirements

### Requirement: One jar per target from one source tree
The build SHALL produce one mod jar for each configured `<minecraft>-<loader>`
target from a single shared source tree. The targets are `1.21.1-neoforge`,
`1.21.1-fabric`, `26.1-neoforge`, `26.1-fabric`, `26.1-vanilla`,
`26.2-neoforge`, `26.2-fabric`, `26.2-vanilla`, `26.3-neoforge`, `26.3-fabric`
and `26.3-vanilla`. The `-vanilla` targets run without a mod loader.

#### Scenario: Full build produces every target
- **WHEN** a developer runs the root build task
- **THEN** a jar is produced for each of the eleven targets
- **AND** each jar's file name includes the mod version, the Minecraft version
  and the loader (e.g. `blockpilot-0.1.0+26.1.2-fabric.jar`,
  `blockpilot-0.1.0+26.3-vanilla.jar`)

#### Scenario: Jar loads only on its own loader
- **WHEN** the `1.21.1-fabric` jar is installed in a Fabric 1.21.1 client
- **THEN** the mod loads and opens its agent endpoint
- **AND** the jar contains no NeoForge-specific classes

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

### Requirement: Adding a target needs no source fork
Adding a Minecraft version or loader SHALL only need a new target declaration
with its own version properties, plus inline version conditions, rename rules,
or per-version source copies where the game API differs. It SHALL NOT need a
copy of the shared source tree.

#### Scenario: Adding 26.1 targets
- **WHEN** `26.1-neoforge` and `26.1-fabric` targets are declared with their
  properties
- **THEN** the build produces jars for them alongside the 1.21.1 jars
- **AND** shared code that compiles unchanged on every target keeps a single
  copy

#### Scenario: Adding 26.2 and 26.3 targets
- **WHEN** the 26.2 and 26.3 targets are declared with their properties
- **THEN** the build produces jars for them alongside the other jars
- **AND** they compile from the same sources as the 26.1 targets, so a class
  that differs between those versions keeps a single copy with inline
  conditions

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

### Requirement: Mod metadata generated per target
Each loader's jar SHALL include loader metadata generated from shared mod
properties plus that target's version ranges. The metadata declares the mod as
client-side for this change. The vanilla jar SHALL carry a Java agent manifest
instead, with the mod version and the Minecraft version it runs on.

#### Scenario: Version range enforced by loader
- **WHEN** the 1.21.1 jar is installed on a different Minecraft version
- **THEN** the loader refuses to load it and reports the version mismatch

#### Scenario: Vanilla jar as an agent
- **WHEN** a vanilla jar's manifest is read
- **THEN** it names the agent's entry class, the mod version and the Minecraft
  version, and the jar has no loader metadata

## ADDED Requirements

### Requirement: One script tests every target
One script SHALL test every configured target, or those it is told to, by
starting that target's client, running its assertions and closing the client. It
SHALL exit non-zero if any assertion fails, and name each failing target and
assertion.

#### Scenario: Every target passes
- **WHEN** a developer runs the script with no arguments and every assertion
  holds
- **THEN** it reports each of the eleven targets as passed and exits with 0

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
  targets count as Fabric, whose replies they share
- **THEN** the script reports the query and both targets, and exits non-zero

### Requirement: The script asserts effects, not only replies
The script SHALL check what input and capture do in the game on every target:
each mouse button, a camera turn, typing and the select-all shortcut, a key the
game polls for, and that captured frames and videos are upright. An assertion it
can't run on a target SHALL be reported as skipped with the reason, not passed.

#### Scenario: Mouse buttons do what their names say
- **WHEN** the script clicks each button in a world, aimed at a block
- **THEN** `left` breaks the block, `right` places the held block and `middle`
  picks the block into the hand

#### Scenario: Frames are upright
- **WHEN** the script captures a frame looking level over a flat world
- **THEN** the top of the frame is sky and the bottom is ground, in both a
  `pixels` read and a frame of a recorded video

#### Scenario: Recording needs ffmpeg
- **WHEN** ffmpeg isn't installed and the script reaches the video assertion
- **THEN** that assertion is reported as skipped, naming ffmpeg

#### Scenario: One target skips what another ran
- **WHEN** an assertion is skipped on one target and ran on another in the same
  run, as when a port breaks audio capture and the game answers
  `err unsupported`
- **THEN** the target that skipped it fails, naming the assertion and the reason

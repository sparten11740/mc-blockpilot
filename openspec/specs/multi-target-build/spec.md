# multi-target-build Specification

## Purpose

Builds Blockpilot from a single source tree into one mod jar per supported
Minecraft version and mod loader. Agents see the same behavior on every target,
and new targets can be added without forking the code.

## Requirements

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

### Requirement: Loader access goes through a platform seam
Code shared by all targets SHALL reach loader-specific functionality only
through a platform abstraction. Each loader provides its own implementation, and
only that loader's jar contains it.

#### Scenario: Shared code is loader-agnostic
- **WHEN** shared code needs a loader service (config directory, key mapping
  registration, tick or render hooks)
- **THEN** it calls the platform abstraction
- **AND** the build fails if shared code references a loader-only class directly

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

### Requirement: Mod metadata generated per target
Each loader's jar SHALL include loader metadata generated from shared mod
properties plus that target's version ranges. The metadata declares the mod as
client-side for this change. The vanilla jar SHALL carry a Java agent manifest
instead, with the mod version and the Minecraft version it runs on.

#### Scenario: Version range enforced by loader
- **WHEN** the 1.21.1 jar is installed on a different Minecraft version
- **THEN** the loader refuses to load it and reports the version mismatch

#### Scenario: Forge metadata
- **WHEN** the `1.20.1-forge` jar is read
- **THEN** it has a `META-INF/mods.toml` naming Minecraft 1.20.1, Forge
  `[47.1,)` and the loader version `[47,)`, and a mixin refmap

#### Scenario: Vanilla jar as an agent
- **WHEN** a vanilla jar's manifest is read
- **THEN** it names the agent's entry class, the mod version and the Minecraft
  version, and the jar has no loader metadata

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

### Requirement: Shared code runs on Java 17
Code that every target compiles, and the extension API jar, SHALL use only the
Java 17 language and class library, so the 1.20.1 jars run on the Java 17 that
1.20.1 is played with. Each target still builds with its own Java version.

#### Scenario: A Java 21 call in shared code
- **WHEN** shared code calls `List.getFirst()`, which Java 17 lacks
- **THEN** the `1.20.1-fabric` build fails, naming the call

#### Scenario: API jar on Java 17
- **WHEN** a 1.20.1 mod compiles against the `blockpilot-api` artifact with
  Java 17
- **THEN** it compiles and runs with the 1.20.1 Blockpilot jars

### Requirement: Blockpilot works beside a replaced world renderer
Beside a mod that replaces how the game draws the world, such as Sodium, or
Embeddium on 1.20.1 Forge, the game SHALL start, every command SHALL work as
without it, and that mod's drawing SHALL stay as it is.

#### Scenario: Sodium on 1.21.1 NeoForge
- **WHEN** the 1.21.1 NeoForge client starts with Sodium in its mods folder
- **THEN** it reaches the title screen, and the test script passes on it

#### Scenario: Embeddium on 1.20.1 Forge
- **WHEN** the 1.20.1 Forge client starts with Embeddium
- **THEN** it reaches the title screen, and the test script passes on it

#### Scenario: A camera away beside Sodium
- **WHEN** an agent runs `camera at 1000,-58,0 look 1000,-60,5 away; settle`
  beside Sodium
- **THEN** `settle` answers `settled=`, and the frame shows the world around
  the camera

### Requirement: Game tests run in CI
Every push to main SHALL run the test script's smoke and security suites on
every configured target in CI, on Linux, one target per job. The run SHALL
fail if any target fails, naming each failing target and assertion, and SHALL
keep each target's logs. It SHALL also run by hand for chosen targets. A
release SHALL NOT wait for it.

#### Scenario: A push breaks one target
- **WHEN** a push to main makes an assertion fail on `1.20.1-forge` only
- **THEN** the game-test run fails, its summary names `1.20.1-forge` and the
  assertion, and the other targets show as passed

#### Scenario: Logs of a passing run
- **WHEN** every target passes
- **THEN** each target's build, client and suite logs are still kept with the
  run

#### Scenario: A new target
- **WHEN** a target is added to `settings.gradle.kts`
- **THEN** the next CI run tests it too, with no change to the workflow

#### Scenario: Chosen targets by hand
- **WHEN** a developer starts the game tests by hand for `26.3-fabric`
- **THEN** only that target runs

#### Scenario: A release tag
- **WHEN** a release tag is pushed
- **THEN** the release builds and publishes without running or waiting for
  the game tests

### Requirement: CI runs what a desktop runs
In CI the clients SHALL have a display, rendering, ffmpeg with an H.264
encoder and an audio device, so every assertion a desktop with ffmpeg runs
runs there too. In CI, an assertion skipped for any reason other than
needing Baritone or a second client SHALL fail its target.

#### Scenario: Recording with sound in CI
- **WHEN** the smoke test reaches the recording and recording-with-sound
  assertions in CI
- **THEN** both run and pass, as on a desktop with ffmpeg

#### Scenario: CI loses its audio device
- **WHEN** the clients in CI find no audio device and the smoke test skips
  recording with sound
- **THEN** each target fails, naming the assertion and why it was skipped

### Requirement: Results combine across machines
The test script SHALL save each target's results, and SHALL combine results
saved on several machines into one report that applies the checks across
targets, JSON shapes within a loader and an assertion skipped on one target
but run on another, as if the targets had run in one run. A configured
target with no saved results SHALL fail the combined report.

#### Scenario: Shapes differ between jobs
- **WHEN** `26.2-fabric` and `26.3-fabric` ran in separate jobs and a query's
  JSON reply has different fields on them
- **THEN** the combined report names the query and both targets, and fails

#### Scenario: Skipped in one job, run in another
- **WHEN** recording with sound was skipped on `26.3-neoforge` and ran on
  `26.2-neoforge`, in separate jobs
- **THEN** the combined report fails `26.3-neoforge`, naming the assertion and
  why it was skipped

#### Scenario: A job left no results
- **WHEN** a target's job was cancelled before it saved its results
- **THEN** the combined report names that target as missing and fails

## ADDED Requirements

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

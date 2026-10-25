# multi-target-build Specification

## Purpose

Builds Blockpilot from a single source tree into one mod jar per supported
Minecraft version and mod loader. Agents see the same behavior on every target,
and new targets can be added without forking the code.

## Requirements

### Requirement: One jar per target from one source tree
The build SHALL produce one mod jar for each configured `<minecraft>-<loader>`
target from a single shared source tree. The first targets are `1.21.1-neoforge`
and `1.21.1-fabric`.

#### Scenario: Full build produces every target
- **WHEN** a developer runs the root build task
- **THEN** a jar is produced for `1.21.1-neoforge` and for `1.21.1-fabric`
- **AND** each jar's file name includes the mod version, the Minecraft version
  and the loader (e.g. `blockpilot-0.1.0+1.21.1-fabric.jar`)

#### Scenario: Jar loads only on its own loader
- **WHEN** the `1.21.1-fabric` jar is installed in a Fabric 1.21.1 client
- **THEN** the mod loads and opens its agent endpoint
- **AND** the jar contains no NeoForge-specific classes

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
documented capability gaps that the target reports through `help`.

#### Scenario: Same reply on both loaders
- **WHEN** the same `status` command is sent to the 1.21.1 NeoForge and 1.21.1
  Fabric builds in equivalent game states
- **THEN** both replies have identical structure and field order

### Requirement: Mod metadata generated per target
Each jar SHALL include loader metadata generated from shared mod properties plus
that target's version ranges. The metadata declares the mod as client-side for
this change.

#### Scenario: Version range enforced by loader
- **WHEN** the 1.21.1 jar is installed on a different Minecraft version
- **THEN** the loader refuses to load it and reports the version mismatch

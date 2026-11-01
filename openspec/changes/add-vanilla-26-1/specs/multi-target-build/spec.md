# Spec Delta

## MODIFIED Requirements

### Requirement: One jar per target from one source tree
The build SHALL produce one mod jar for each configured `<minecraft>-<loader>`
target from a single shared source tree. The targets are `1.21.1-neoforge`,
`1.21.1-fabric`, `26.1-neoforge`, `26.1-fabric` and `26.1-vanilla`, which runs
without a mod loader.

#### Scenario: Full build produces every target
- **WHEN** a developer runs the root build task
- **THEN** a jar is produced for `1.21.1-neoforge`, `1.21.1-fabric`,
  `26.1-neoforge`, `26.1-fabric` and `26.1-vanilla`
- **AND** each jar's file name includes the mod version, the Minecraft version
  and the loader (e.g. `blockpilot-0.1.0+26.1.2-fabric.jar`)

#### Scenario: Jar loads only on its own loader
- **WHEN** the `1.21.1-fabric` jar is installed in a Fabric 1.21.1 client
- **THEN** the mod loads and opens its agent endpoint
- **AND** the jar contains no NeoForge-specific classes

#### Scenario: 26.1 jar on 26.1
- **WHEN** the `26.1-neoforge` jar is installed in a NeoForge 26.1.2 client,
  or the `26.1-fabric` jar in a Fabric 26.1.2 client
- **THEN** the mod loads and opens its agent endpoint

### Requirement: Mod metadata generated per target
Each loader's jar SHALL include loader metadata generated from shared mod
properties plus that target's version ranges. The metadata declares the mod as
client-side for this change. The vanilla jar SHALL carry a Java agent manifest
instead, with the mod version.

#### Scenario: Version range enforced by loader
- **WHEN** the 1.21.1 jar is installed on a different Minecraft version
- **THEN** the loader refuses to load it and reports the version mismatch

#### Scenario: Vanilla jar as an agent
- **WHEN** the `26.1-vanilla` jar's manifest is read
- **THEN** it names the agent's entry class and the mod version, and the jar
  has no loader metadata

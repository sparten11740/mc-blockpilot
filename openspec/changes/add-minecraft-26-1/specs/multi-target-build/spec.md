# Spec Delta

## MODIFIED Requirements

### Requirement: One jar per target from one source tree
The build SHALL produce one mod jar for each configured `<minecraft>-<loader>`
target from a single shared source tree. The targets are `1.21.1-neoforge`,
`1.21.1-fabric`, `26.1-neoforge` and `26.1-fabric`.

#### Scenario: Full build produces every target
- **WHEN** a developer runs the root build task
- **THEN** a jar is produced for `1.21.1-neoforge`, `1.21.1-fabric`,
  `26.1-neoforge` and `26.1-fabric`
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

### Requirement: Identical agent-facing behavior across targets
For the same game situation, every target SHALL accept the same commands and
produce the same reply format. Version-specific differences show up only as
documented capability gaps that the target reports through `help`.

#### Scenario: Same reply on both loaders
- **WHEN** the same `status` command is sent to the 1.21.1 NeoForge and 1.21.1
  Fabric builds in equivalent game states
- **THEN** both replies have identical structure and field order

#### Scenario: Same reply on both Minecraft versions
- **WHEN** the smoke test runs against a 1.21.1 jar and a 26.1 jar of the same
  loader
- **THEN** every step passes on both, and the JSON replies have the same
  fields

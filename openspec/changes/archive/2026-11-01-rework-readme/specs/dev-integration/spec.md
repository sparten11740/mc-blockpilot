# Spec Delta

## MODIFIED Requirements

### Requirement: Jars available to other dev environments
The build SHALL publish each target's mod jar and the extension API
(`blockpilot-api`) to mavenLocal under `dev.blockpilot`. The documentation,
linked from the README, SHALL show how to add Blockpilot as a runtime-only
dependency in ModDevGradle and Fabric Loom projects, with the auto-join
properties.

#### Scenario: NeoForge dev env
- **WHEN** a NeoForge 1.21.1 mod project adds the documented runtime-only
  dependency and runs `runClient`
- **THEN** Blockpilot loads in that client next to the mod under development,
  and its endpoint starts

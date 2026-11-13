## MODIFIED Requirements

### Requirement: Optional, version-independent API
The extension API SHALL contain no Minecraft or loader types. It SHALL be
published as a compile-only artifact that is the same for every Minecraft
version and loader. A mod that integrates only when Blockpilot is installed
SHALL keep working without Blockpilot.

#### Scenario: Integrating mod without Blockpilot
- **WHEN** a mod that registers Blockpilot commands only when the `blockpilot`
  mod is loaded runs without Blockpilot installed
- **THEN** it loads and works normally, and no Blockpilot class is loaded

#### Scenario: One API jar for all targets
- **WHEN** a mod compiles against the published `blockpilot-api` artifact
- **THEN** the same compiled integration works with the 1.20.1, 1.21.1,
  1.21.11, 26.1, 26.2 and 26.3 Blockpilot jars, on NeoForge, Forge and Fabric

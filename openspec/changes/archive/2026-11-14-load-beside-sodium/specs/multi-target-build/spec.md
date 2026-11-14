## ADDED Requirements

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

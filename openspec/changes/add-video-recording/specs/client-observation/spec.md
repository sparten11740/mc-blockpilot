# Spec Delta

## MODIFIED Requirements

### Requirement: Supported options
The option names SHALL be those of the running version's `options.txt`:
`graphicsMode`, `particles`, `renderDistance`, `simulationDistance`,
`renderClouds`, `ao`, `entityShadows`, `biomeBlendRadius`, `mipmapLevels`,
`entityDistanceScaling`, `maxFps`, `enableVsync`, `prioritizeChunkUpdates`,
`fov`, `gamma`, `guiScale`, `bobView`, `fovEffectScale`, `screenEffectScale`,
`darknessEffectScale`, `damageTiltStrength`, `hideLightningFlashes`,
`glintStrength` and `glintSpeed`, and the volume of each sound category,
`soundCategory_<category>` (`soundCategory_master`, `soundCategory_music`,
`soundCategory_block`, …) from 0 to 1.

#### Scenario: Unknown option
- **WHEN** an agent runs `option shaderPack on`
- **THEN** the result is `err syntax` pointing to `option` for the list

#### Scenario: Music off for a take
- **WHEN** an agent runs `option soundCategory_music 0` while music plays,
  and later `option reset`
- **THEN** the music falls silent at once, and the reset brings back the
  player's music volume

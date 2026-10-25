# Spec Delta

## MODIFIED Requirements

### Requirement: Client options
`option` SHALL list the supported client options with their values, showing the
original value of each one an agent changed. `option <name>` SHALL report the
value and what the option accepts, `option <name> <value>` SHALL set it, and
`option reset` SHALL restore every option agents changed, including options the
game changed along with the one an agent set. Blockpilot SHALL NOT write
`options.txt`.

#### Scenario: Flames gone on Fast
- **WHEN** an agent runs `option graphicsMode fast; screenshot; option reset`
- **THEN** the screenshot shows the rebuilt terrain in Fast graphics, and the
  reset brings back the player's previous mode

#### Scenario: A preset and what it set
- **WHEN** on 26.1 an agent runs `option graphicsPreset fast`, which also sets
  the render distance, the leaves and a dozen other options, and then
  `option reset`
- **THEN** the preset and every option it set are back at the player's values

### Requirement: Supported options
The option names SHALL be those of the running version's `options.txt`:
`graphicsMode`, `particles`, `renderDistance`, `simulationDistance`,
`renderClouds`, `ao`, `entityShadows`, `biomeBlendRadius`, `mipmapLevels`,
`entityDistanceScaling`, `maxFps`, `enableVsync`, `prioritizeChunkUpdates`,
`fov`, `gamma`, `guiScale`, `bobView`, `fovEffectScale`, `screenEffectScale`,
`darknessEffectScale`, `damageTiltStrength`, `hideLightningFlashes`,
`glintStrength` and `glintSpeed`, plus `hud` (`on|off`, the F1 switch). From
26.1 on, where Fast, Fancy and Fabulous became presets, `graphicsPreset`,
`cutoutLeaves` and `improvedTransparency` take the place of `graphicsMode`.

#### Scenario: Unknown option
- **WHEN** an agent runs `option shaderPack on`
- **THEN** the result is `err syntax` pointing to `option` for the list

#### Scenario: Graphics on 26.1
- **WHEN** an agent runs `option graphicsMode fast` on 26.1
- **THEN** the result is `err syntax`, and `option` lists `graphicsPreset`,
  `cutoutLeaves` and `improvedTransparency`

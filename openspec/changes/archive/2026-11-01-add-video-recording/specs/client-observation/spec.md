# Spec Delta

## ADDED Requirements

### Requirement: The sound volume options
Besides those of `Supported options`, `option` SHALL take the volume of each
sound category, from 0 to 1, by its name in the running version's
`options.txt`: `soundCategory_<category>` (`soundCategory_master`,
`soundCategory_music`, `soundCategory_block`, …).

#### Scenario: Music off for a take
- **WHEN** an agent runs `option soundCategory_music 0` while music plays,
  and later `option reset`
- **THEN** the music falls silent at once, and the reset brings back the
  player's music volume

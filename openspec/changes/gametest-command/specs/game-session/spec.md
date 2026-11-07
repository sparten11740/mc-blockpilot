## ADDED Requirements

### Requirement: Worlds using experimental settings
When the game asks, before loading a world that `join world` or `snapshot save`
or `restore` opens, whether to back it up as it uses experimental settings,
Blockpilot SHALL load a test world made by `create world` without a backup,
adding `experimental=true`, and SHALL fail with `err join_failed` for any other
world, the question left on screen. Other backup questions SHALL stay for a
person.

#### Scenario: A test world with a data pack's GameTests
- **WHEN** an agent runs `join world <folder>` on 26.x for a test world whose
  data pack adds GameTests, which the game counts as experimental settings
- **THEN** the world loads without the question waiting, and the result is
  `ok joined <folder> experimental=true`, `<folder>` being the save folder's
  name, as in `ok joined New World`

#### Scenario: A player's own world
- **WHEN** an agent runs `join world <folder>` for a world not made by
  `create world`, about which the game asks the same question
- **THEN** the result is `err join_failed` naming the question, which stays on
  screen for a person to answer

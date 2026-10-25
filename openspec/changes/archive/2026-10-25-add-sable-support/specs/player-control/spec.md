# Spec Delta

## MODIFIED Requirements

### Requirement: Raw keyboard and key mappings
`key <down|up|press> <key> [Nt]` SHALL feed key events into the game's keyboard
handling. `<key>` is a key name (`w`, `space`, `left_shift`, `e`, `f5`) or a
key-mapping id (`key.use`, `key.inventory`, or a mod's mapping id), resolved to
its currently bound key. A key name may separate its words with `_` or `.`
(`left_shift`, `left.shift`), and an unknown one fails with the names closest
to it. `type <text>` SHALL send character input to the focused text field.

#### Scenario: Modded key mapping
- **WHEN** a mod registers a key mapping and the agent runs `key press <that
  mapping id>`
- **THEN** the mod's handler fires as if the user pressed the bound key

#### Scenario: Key name as help writes it
- **WHEN** an agent runs `key press left_shift 5t`
- **THEN** the left shift key is held for 5 ticks, the same as with
  `left.shift`

#### Scenario: Misspelled key
- **WHEN** an agent runs `key press leftshift`
- **THEN** the result is `err syntax` naming `left_shift` among the closest
  key names

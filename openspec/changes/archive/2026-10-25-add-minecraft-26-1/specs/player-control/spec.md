# Spec Delta

## MODIFIED Requirements

### Requirement: Crafting
`craft <item> [x<n>]` SHALL craft through the vanilla recipe-book placement
mechanism. It uses the 2x2 inventory grid when the recipe fits, or an
open/nearby crafting table (within 4 blocks), and fails with `err no_recipe`,
`err missing <item> x<n>` or `err need_table`. From Minecraft 26.1 on, a
client on a remote server knows only the recipes the player has unlocked, so
there `craft` finds only those, and `help craft` says so. In singleplayer it
finds every recipe, as on 1.21.1.

#### Scenario: Craft planks
- **WHEN** an agent with 6 oak logs runs `craft oak_planks x24`
- **THEN** 24 oak planks end up in the inventory and the result is `ok craft 24
  oak_planks`

#### Scenario: Locked recipe in singleplayer
- **WHEN** an agent in a singleplayer world on 26.1 holds the ingredients of a
  recipe the player hasn't unlocked and runs `craft` for it
- **THEN** the item is crafted, as on 1.21.1

#### Scenario: Locked recipe on a remote server
- **WHEN** an agent on a remote 26.1 server runs `craft` for a recipe the
  player hasn't unlocked
- **THEN** the result is `err no_recipe <item>`

# Spec Delta

## ADDED Requirements

### Requirement: Use clicks only its target
`use <pos>` on a block in the world and `use e<id>` SHALL click only when,
after aiming, the crosshair rests on the target. Otherwise they SHALL fail
with `err obstructed <what> <where>`, naming what is in the way, or with `err
out_of_reach`, and use nothing. A target block without a shape, such as air,
only sets the aim. `use … any` SHALL skip the check.

#### Scenario: Lever behind glass
- **WHEN** an agent runs `use <pos of a lever>` and a glass pane stands
  between the player and the lever
- **THEN** the result is `err obstructed` with the glass pane's id and
  position, and the lever is not switched

#### Scenario: Entity behind a fence
- **WHEN** an agent runs `use e<id>` on a villager and a fence is first on
  the line of sight
- **THEN** the result is `err obstructed` naming the fence, and the fence is
  not used

#### Scenario: Clear line
- **WHEN** an agent runs `use <pos of a lever>` with nothing in the way
- **THEN** the lever is switched and the reply's `aim` names it

### Requirement: Use any reports a click elsewhere
When `use <target> any` clicks a block or entity other than its target, at a
world or a plot position, the reply SHALL carry `off_target=<target>` beside
the `aim` of what it clicked.

#### Scenario: Another block of a panel
- **WHEN** an agent runs `use <pos of a key> any` and another block of the
  same panel is first on the line of sight
- **THEN** the panel's block is clicked, and the reply's `aim` names it and
  carries `off_target=<pos of the key>`

#### Scenario: On target
- **WHEN** an agent runs `use <pos> any` and the crosshair rests on that block
- **THEN** the reply carries no `off_target`

### Requirement: Sneaking use names the item it used
`use … sneak` SHALL report `sneak_item=<id>` when either hand holds an item,
naming the main hand's item, else the off hand's: the game then skips the
block's own use and uses that item on it instead.

#### Scenario: Block in hand
- **WHEN** an agent holding cobblestone runs `use <pos of a chest> sneak`
- **THEN** the reply carries `sneak_item=cobblestone`, and no chest screen
  opens

#### Scenario: Empty hands
- **WHEN** an agent with both hands empty runs `use <pos of a lever> sneak`
- **THEN** the reply carries no `sneak_item`

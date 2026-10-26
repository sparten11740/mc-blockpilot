# Spec Delta

## ADDED Requirements

### Requirement: Server commands run from the player
Without `as=player`, `server` SHALL run the command as the server's own
source, positioned at the player with the player's rotation and in the
player's dimension, so `~`, `^`, `@p` and `distance=` count from the player.
When no player is in the world, it SHALL run at the overworld's spawn.

#### Scenario: Relative block
- **WHEN** an agent standing far from the world spawn runs `server setblock ~
  ~-1 ~ minecraft:stone`
- **THEN** the block under the player becomes stone

#### Scenario: Entities near the player
- **WHEN** a dropped item lies next to the player, none lies near the spawn,
  and an agent runs `server execute if entity @e[type=item,distance=..8]`
- **THEN** the result is `ok result=1 success=true`

#### Scenario: Another dimension
- **WHEN** an agent in the Nether runs `server setblock ~ ~-1 ~
  minecraft:stone`
- **THEN** the block under the player in the Nether becomes stone, and the
  overworld is unchanged

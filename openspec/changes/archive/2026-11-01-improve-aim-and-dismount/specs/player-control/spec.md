## ADDED Requirements

### Requirement: A bare sneak taps it
A bare `sneak` SHALL tap sneak: hold it for a few ticks and let go, long enough
for the server to see it, which dismounts a seated or riding player. `sneak on`
and `sneak off` keep holding and releasing it.

#### Scenario: Dismount with a tap
- **WHEN** an agent riding a minecart runs `sneak`
- **THEN** the player leaves the minecart, and sneak is no longer held once
  the command has answered

### Requirement: Face says how far a missed target is
When `face` ends with the crosshair on nothing, its reply SHALL add
`dist=<blocks>`, from the player's eyes to the point it aimed at, and
`reach=<blocks>`, the player's interaction range for that kind of target:
the block range for a position, the entity range for an entity. Both are
given to the hundredth. A reply whose crosshair rests on a block or entity
carries neither.

#### Scenario: Target beyond reach
- **WHEN** an agent runs `face ~8,0,0` on a block 8 blocks away, in survival
- **THEN** the reply is `aim:nothing` with `dist=` above 7 and `reach=4.5`

#### Scenario: Empty air within reach
- **WHEN** an agent runs `face ~2,1,0` where that cell and the line to it are
  air
- **THEN** the reply is `aim:nothing` with `dist=` below `reach=`

#### Scenario: Target hit
- **WHEN** an agent runs `face ~2,0,1` on a block within reach
- **THEN** the reply names the block and carries no `dist=` or `reach=`

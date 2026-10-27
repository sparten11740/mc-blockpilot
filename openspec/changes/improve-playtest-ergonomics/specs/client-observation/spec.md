# Spec Delta

## MODIFIED Requirements

### Requirement: Camera placement
`camera at <pos> [look <pos|e<id>>]` SHALL render the view from the given world
point, facing the given target, with the player model visible, until `camera
reset`, or `camera off`, which does the same. The reply gives the camera's
absolute position and look point. While placed, the player still moves and
acts as usual, and the GUI and hand are hidden as with F1, until the reset
restores them. Leaving the world, by any means, and turning agent control off
with the agent-control key reset the camera.

#### Scenario: Outside view of the airliner
- **WHEN** an agent runs `camera at 40,90,-20 look e812; screenshot`
- **THEN** the screenshot shows the scene from that point toward the entity, and
  `camera reset` returns to first person

#### Scenario: Camera off
- **WHEN** an agent runs `camera off` with the camera placed
- **THEN** the view returns to first person, as with `camera reset`

### Requirement: Camera placement reports its block
The reply to `camera at` SHALL name the block the camera sits in. When that
block is solid, so the view would show its inside, the reply SHALL say so with
`inside=<block>`, and the camera is placed all the same. When the camera sits
in a chunk the client hasn't loaded, which renders as empty sky, the reply
SHALL carry `unloaded` instead of a block.

#### Scenario: Camera in a panel
- **WHEN** an agent runs `camera at <position inside an overhead panel>`
- **THEN** the reply carries `inside=` with the panel's block id, warning that
  the view starts inside a solid block

#### Scenario: Camera in the air
- **WHEN** an agent runs `camera at ~0,10,0`
- **THEN** the reply names `air` as the camera's block and carries no `inside=`

#### Scenario: Camera beyond the loaded chunks
- **WHEN** an agent runs `camera at <a point 2000 blocks from the player>`
- **THEN** the reply carries `unloaded` and names no block

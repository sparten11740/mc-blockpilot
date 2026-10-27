# Spec Delta

## MODIFIED Requirements

### Requirement: Camera placement
`camera at <pos> [look <pos|e<id>>]` SHALL render the view from the given world
point, facing the given target, with the player model visible, until `camera
reset` (or `camera off`). The reply gives the camera's absolute position and
look point. While placed, the player still moves and acts as usual, and the GUI
and hand are hidden as with F1, until the reset restores them. Leaving the
world, by any means, and turning agent control off with the agent-control key
reset the camera.

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

### Requirement: Settling after an option change
After a change, `option` SHALL wait as `settle` does: it answers once every
chunk within the view distance of the player has arrived and the chunk compile
queue has stayed empty for 3 ticks, with `settled=<seconds>`, or at `timeout=`
(default 20s) with `unsettled pending=<n>`, and `missing=<n>` while chunks
have yet to arrive. Either way the option keeps its new value.

#### Scenario: Render distance raised
- **WHEN** an agent runs `option renderDistance 16`
- **THEN** the reply comes after the newly visible chunks have arrived and
  their sections have compiled, with `settled=` and the time it took

## ADDED Requirements

### Requirement: Waiting for the view to settle
`settle [timeout=<duration>]` SHALL answer once every chunk within the view
distance of the player has arrived and the chunk compile queue has stayed
empty, with the same number of sections rendered, for 3 ticks, with
`settled=<seconds>`, or at `timeout=` (default 20s) with `unsettled
pending=<n>`, plus `missing=<n>` while chunks have yet to arrive. It SHALL
change nothing.

#### Scenario: Camera moved far
- **WHEN** an agent runs `camera at ~150,0,0; settle; screenshot`
- **THEN** `settle` answers with `settled=` once the sections around the
  camera have compiled, and the screenshot shows them

#### Scenario: Nothing new in view
- **WHEN** an agent runs `settle` while the view hasn't changed
- **THEN** it answers with `settled=` after a few ticks

#### Scenario: New terrain still generating
- **WHEN** an agent raises the render distance where the terrain around the
  player isn't generated yet, and runs `settle timeout=120s`
- **THEN** `settle` answers once the chunks within the new view distance have
  arrived, so `block` at the edge of that distance finds its chunk loaded

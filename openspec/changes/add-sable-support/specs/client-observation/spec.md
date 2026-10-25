# Spec Delta

## ADDED Requirements

### Requirement: Camera placement reports its block
The reply to `camera at` SHALL name the block the camera sits in. When that
block is solid, so the view would show its inside, the reply SHALL say so with
`inside=<block>`, and the camera is placed all the same.

#### Scenario: Camera in a panel
- **WHEN** an agent runs `camera at <position inside an overhead panel>`
- **THEN** the reply carries `inside=` with the panel's block id, warning that
  the view starts inside a solid block

#### Scenario: Camera in the air
- **WHEN** an agent runs `camera at ~0,10,0`
- **THEN** the reply names `air` as the camera's block and carries no `inside=`

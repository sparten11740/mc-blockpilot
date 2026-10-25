# Spec Delta

## ADDED Requirements

### Requirement: Aiming at a point within a block
`face <pos> at <u,v,w>` and `use <pos> at <u,v,w>` SHALL aim at a point inside
the block at `pos`, each coordinate from 0 to 1, in the block's own frame: the
frame its model is drawn in facing north (u west to east, v bottom to top, w
north to south), turned about the vertical by the block's horizontal facing as
block models turn. Without a horizontal facing the frame is unturned. A
coordinate outside 0 to 1 SHALL fail with `err syntax`.

#### Scenario: Key on a panel facing east
- **WHEN** a panel block's model has a key at 0.75,0.5,0.06 and an agent runs
  `use <pos> at 0.75,0.5,0.06` on such a panel placed facing east
- **THEN** the click lands on that key, where the turn by the block's facing
  has put it in the world

#### Scenario: Point read off the crosshair
- **WHEN** an agent aims at a key by hand, reads `hit=0.73,0.86,0.34` from
  `status`, and later runs `use <pos> at 0.73,0.86,0.34`
- **THEN** the click lands on the same point of the block

#### Scenario: Block aboard a sub-level
- **WHEN** an agent runs `use <plot position> at <u,v,w>` on a block aboard a
  moving sub-level
- **THEN** the point follows the sub-level as a whole-block target does

# Spec Delta

## ADDED Requirements

### Requirement: Region dump as a grid
`blocks <pos1> <pos2> grid` SHALL dump the same cuboid with one block id per
cell instead of a palette and run lengths: one line per row, grouped by Y layer
from the bottom, each row north to south and each cell west to east, air as
`air`.

#### Scenario: Two by two
- **WHEN** an agent runs `blocks ~0,-1,0 ~1,-1,1 grid` on grass with one stone
- **THEN** the reply has one layer of two rows of two ids, with `stone` in the
  cell where the stone is

## ADDED Requirements

### Requirement: Place errors say how to recover
When the inventory lacks the item, `place`'s `err no_item` SHALL say to give it
first. When the target cell is taken, the error SHALL name the cell above if
that one is free.

#### Scenario: Hint for a missing item
- **WHEN** an agent runs `place torch ~1,0,0` with no torch in the inventory
- **THEN** the result is `err no_item torch` with a hint to give it first

#### Scenario: Place on a taken cell
- **WHEN** an agent runs `place stone ~0,-1,1` where grass is and air is above
- **THEN** the error names `grass_block` and says `~0,0,1` above is free

## MODIFIED Requirements

### Requirement: Block interaction
The mod SHALL provide `mine <pos>`, `mine <block|#tag> x<n>` (find, walk to and
mine the n nearest), `place <item> <pos> [face]` (select the item and place it
against an adjacent solid face), and `use [<pos>|e<id>]` (right-click a block,
an entity, or the held item). Targets out of reach without a walk fail with `err
out_of_reach`. When the inventory lacks the item, `place`'s `err no_item` SHALL
say to give it first. When the target cell is taken, the error SHALL name the
cell above if that one is free.

#### Scenario: Mine several logs
- **WHEN** an agent runs `mine #logs x6`
- **THEN** the player walks to and mines up to six log blocks, and the result
  reports `ok mine 6/6 oak_log`

#### Scenario: Place without item
- **WHEN** an agent runs `place torch ~1,0,0` with no torch in the inventory
- **THEN** the result is `err no_item torch` with a hint to give it first

#### Scenario: Place on a taken cell
- **WHEN** an agent runs `place stone ~0,-1,1` where grass is and air is above
- **THEN** the error names `grass_block` and says `~0,0,1` above is free

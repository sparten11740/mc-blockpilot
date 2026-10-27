## ADDED Requirements

### Requirement: Ordering blocks found aboard
`sable:find` SHALL take `sort=x|y|z|-x|-y|-z`, ordering the matches by that
coordinate of their plot position (the sub-level's own axes), smallest first or
with `-` largest first, and `near=<pos>`, ordering them by distance from that
point, a world or a plot position, instead of from the player. Matches level on
the sorted axis go nearest first. Both apply to all matches before the limit,
and the reply SHALL carry `count=<n>` when the limit left matches out.

#### Scenario: Block furthest aft
- **WHEN** an agent runs `sable:find ship *booster* 1 sort=-z` on a ship whose
  tail lies toward plot +z
- **THEN** the row is the booster with the largest plot z, however the ship is
  turned and wherever the player stands, with `count=` giving all boosters

#### Scenario: Seat nearest the helm
- **WHEN** an agent runs `sable:find ship *seat* 1 near=<plot position of the
  helm>`
- **THEN** the row is the seat nearest the helm, not the one nearest the player

#### Scenario: All matches shown
- **WHEN** an agent runs `sable:find ship *door* 10` and the ship has 4 doors
- **THEN** the reply lists the 4 doors and carries no `count=`

## ADDED Requirements

### Requirement: Camera clear of blocks
With `clear`, `camera at` SHALL, when the point's cell holds a block with a
collision shape, move the camera along its view line toward the look point, a
quarter block at a time, to the first point whose cell has no collision shape,
at most 8 blocks and never past the look point. The reply SHALL give the
position used as `camera` and the distance moved as `cleared=<blocks>`. With no
such point in reach, the camera stays put and the reply carries `uncleared`.

#### Scenario: Inside a hull
- **WHEN** an agent runs `camera at <a point inside a hull block> look <a point
  in the cabin> clear`
- **THEN** the camera sits at the first point in the cabin past the hull block,
  and the reply carries `cleared=` with the distance and no `inside=`

#### Scenario: Already clear
- **WHEN** an agent runs `camera at ~0,10,0 clear` with air there
- **THEN** the camera sits at that point and the reply carries no `cleared=`

# Proposal

## Why

`sable:info` measures `agl` from the bottom of the sub-level's world bounds.
Sable builds those bounds around the eight turned corners of the plot box, so
while a large sub-level banks or pitches their bottom lies far below any of
its blocks. A sub-level 160 blocks wide and 48 tall, banked 30°, has the
bottom of its bounds about 37 blocks below where it lies level. `agl` then
reads near 0 while the sub-level flies 60 blocks up, and jumps by tens of
blocks with every bank, with no matching climb. A flight trace sampled with
`sable:info <sub> brief` can't tell a bank from a descent, and it doesn't give
the sub-level's height, so the two can't be checked against each other.

## What Changes

- `agl` is measured from the lowest point of the sub-level as it lies now:
  the lowest corner of the block aboard that lies lowest in the world, down to
  the top of the ground straight under that point. A sub-level whose client
  holds none of its blocks gives `agl=none`, as nothing loaded below does.
- `sable:info <sub> brief` also gives `y=`, the world y of the sub-level's
  position, next to `agl`.

## Capabilities

### New Capabilities

None.

### Modified Capabilities
- `sable-sublevels`: where the height above ground is measured from, and the
  flight readout's fields.

## Impact

- `game/sable/SableCommands.java` (`agl`, the brief reply, help and field
  docs) and a new helper in `game/sable` that finds a sub-level's lowest
  point, with its test.
- README: the sable row and the With Sable section.

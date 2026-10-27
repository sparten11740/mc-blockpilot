# Proposal

## Why

Play-tests of a sub-level often need the block at one end of it, such as the
part furthest aft or the seat nearest the helm. `sable:find` orders matches
only by distance from the player and cuts them to its limit (5 by default, at
most 100). An agent that sorts the rows itself only sorts what survived the
cut, and nothing in the reply says that matches were left out. So it can
quietly pick the wrong block.

## What Changes

- `sable:find` takes `sort=x|y|z|-x|-y|-z`, which orders the matches along
  the sub-level's own plot axes, smallest first, or largest first with `-`.
- `sable:find` takes `near=<pos>`, which orders the matches by distance from
  that point, a world or a plot position, instead of from the player.
- Both apply to all matches before the limit. The reply carries
  `count=<n>`, the number of matches in all, when the limit left some out, as
  `find` already does.

## Capabilities

### New Capabilities

None.

### Modified Capabilities
- `sable-sublevels`: ordering the blocks found aboard, and saying when the
  limit left some out.

## Impact

- `game/sable/SableCommands.java` (`sable:find` options, ordering, help).
- README: the sable row and the With Sable section.

# Proposal

## Why

`sable:tp` misplaces the player on a sub-level under way. It teleports the
player on the server to the spot's world position under the server's pose. On
the client, Sable runs every sub-level about a tick and a half behind the
server, and the client reports a riding player's position in plot
coordinates, which the server takes as given. So the player lands a tick's
travel ahead of the spot in the sub-level's own frame and stays there. On a
raft at 53 blocks per second the player landed 1.7 blocks ahead of the spot
and 1.5 blocks under the floor, and fell off. At airliner speed the player
lands two or three blocks ahead, often inside a wall or window, so `use` at
the block fails with `err obstructed`, or the player drops off the
sub-level. Nothing in the reply shows either. When the player falls off a
sub-level later, nothing reports that either.

## What Changes

- `sable:tp` places the player on the spot through the client's copy of the
  sub-level, moving with it, until the client shows the player aboard on that
  spot. Only then does it answer, with where the player stands now. A
  flying creative player stops flying, since a flying player never lands.
- `sable:tp` fails with the new `err not_aboard` when the client doesn't show
  the player aboard on the spot within five seconds, saying what the client
  shows instead.
- New events `! sable:boarded <sub>` and `! sable:left <sub>` when the
  sub-level carrying the player (stood on or ridden) changes, so an agent
  learns that the player fell or flew off, or that the sub-level unloaded
  under them.

## Capabilities

### New Capabilities

None.

### Modified Capabilities
- `sable-sublevels`: teleporting aboard lands the player on the chosen spot on
  a moving sub-level and fails when it doesn't; new events report boarding
  and leaving a sub-level.

## Impact

- `game/sable/SableCommands.java` (`sable:tp`), and a new per-tick watcher of
  the sub-level carrying the player in `game/sable/`, called each client tick.
- `protocol/Errors.java`: the `not_aboard` code.
- README: the With Sable section.

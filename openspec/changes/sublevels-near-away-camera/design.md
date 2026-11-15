# Design

## Context

Sable's `SubLevelTrackingSystem.shouldLoad(Player, Vector3dc)` decides each
tick whether a player should track a sub-level: the sub-level's position
within `sub_level_tracking_range` (320 by default) of `player.getX/Y/Z()`.
Blockpilot's away camera moves where the integrated server treats the player
as being for chunks, entity tracking, sounds and spawning, through
`AwayCamera.awayFrom` and `AwayCamera.presence` on the server, but Sable
reads the player's own position. Blockpilot talks to Sable through its
companion API, which has no say in tracking.

## Decisions

### D1: Shift the sub-level's position, not the player's

A `@Pseudo` mixin (`remap = false`, `require = 0`) modifies `shouldLoad`'s
position argument at its head: while the player is away, the sub-level's
position plus the player's offset from the camera. Its distance from the
player is then its distance from the camera, so Sable's own test decides,
with its configured range, and Blockpilot needs neither Sable's config nor
its internal names beyond the method. The handler names only Minecraft and
JOML types, which the Fabric build remaps with the rest of the jar.

Alternatives: injecting at the head and returning the answer would need
Sable's range, through its config class; redirecting `player.getX()` would
need the call's runtime name on each loader.

### D2: Loading follows the chunks

Sable holds a sub-level while its chunks are loaded; the away camera's
chunk tickets already load the camera's chunks, so a ship unloaded while
nobody was near comes back with them. Only the sending needed a change.

## Risks / Trade-offs

- [A later Sable renames `shouldLoad` or changes its arguments] → the mixin
  applies to nothing and ships near an away camera stop showing, as before;
  nothing breaks.
- [Sub-levels near the player stop being sent while the camera is away] →
  as with chunks, by design: away moves the player's presence.

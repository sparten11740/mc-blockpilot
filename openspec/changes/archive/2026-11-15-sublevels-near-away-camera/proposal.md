# Proposal

## Why

A camera placed with `away` shows the world round it wherever it is, so the
player can stand anywhere while it films, but not Sable's sub-levels: Sable
sends a player the sub-levels within its tracking range of the player, so a
ship next to the camera stays on the server and never reaches the client.
Filming ships, the most common thing to film with Sable, then ties every
shot to where the player stands, and a ship that climbs or flies out of the
player's range vanishes from the camera.

## What Changes

- With a camera away, Sable measures its tracking range from the camera
  instead of the player, as the integrated server already does for chunks,
  entities and sounds: the sub-levels near the camera reach the client and
  are drawn, also one the server had unloaded while nobody was near.
- `help camera`, docs/commands.md and docs/sable.md say so instead of
  "Sable still sends its sub-levels by where the player is".

Out of scope: a camera that follows a sub-level, and paths relative to one
(a change of their own), and sub-levels for a camera that isn't away.

## Capabilities

### New Capabilities

None.

### Modified Capabilities
- `sable-sublevels`: a camera placed away receives the sub-levels within
  Sable's tracking range of it.

## Impact

- **Mixins:** one `@Pseudo` mixin into Sable's sub-level tracking, skipped
  without Sable, on the 1.21.1 targets, the only ones with the integration.
- **Docs:** help, docs/commands.md, docs/sable.md, CHANGELOG under 1.4.0.

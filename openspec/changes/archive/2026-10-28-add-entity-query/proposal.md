# Proposal

## Why

To study how a moving entity behaves, such as a thrown item, a projectile, a
mob or a vehicle, an agent needs its position, rotation and velocity tick by
tick, as the client shows it and as the integrated server simulates it. No
query gives that today. `look` lists entities relative to the player, rounded
to tenths and without velocity. `nbt e<id> path=Pos` gives only the server's
data, as NBT. `status` gives motion only for the player. So jitter, where the
client's interpolation and the server's corrections disagree, can only be
seen with a temporary mixin.

## What Changes

- A new read-only `entity <e<id>> [side=server]` answers one entity's type,
  absolute position, yaw, pitch, velocity in blocks per tick and whether it
  is on the ground, as the client has it. With `side=server` it answers as
  the integrated server has it, and adds the UUID.
- An entity the side read doesn't hold fails with `err no_entity`.
- Sampled on both sides at once, for example
  `sample entity e5 and entity e5 side=server every 1t x100`, it gives the
  client's and the server's row for each tick, so drift shows row by row.

## Capabilities

### New Capabilities

None.

### Modified Capabilities
- `game-perception`: a query for one entity's motion, on either side.

## Impact

- `game/PerceptionCommands.java`: the `entity` query in the perception group.
- `protocol/Formatter.java`: JSON writes position coordinates to the
  thousandth, not the hundredth.
- README: the perception row.

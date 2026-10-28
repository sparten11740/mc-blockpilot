# Proposal

## Why

Using `entity` to study a moving body showed three gaps:

- The player can't be named. `entity` takes `e<id>`, and no reply gives the
  player's own id, so the player's motion can't be sampled on both sides.
- A copy that was removed and created again looks like one that moved. The
  entity's age, its tick count, tells them apart.
- Jitter often comes from how position updates arrive: none for a few ticks,
  then several at once. Seeing that took a debug build that logged every
  update the client applied.

## What Changes

- `entity me` names the player, on the client and with `side=server`, and
  every `entity` reply names the entity's `e<id>`.
- `entity` replies give the entity's `age` in ticks.
- On the client, `entity` replies give the updates the client applied to the
  entity since its previous tick, by kind (move, teleport, motion, data),
  leaving the field out when none arrived.

## Capabilities

### New Capabilities

None.

### Modified Capabilities
- `game-perception`: the entity query names the player and the entity, gives
  its age, and on the client the updates it received.

## Impact

- `game/PerceptionCommands.java` (the `entity` query).
- A counter of the client's entity updates per tick, fed by mixins on the
  client's packet listener for both versions, and reset at the end of each
  client tick by `game/BlockpilotClient.java`.
- README: the perception row.

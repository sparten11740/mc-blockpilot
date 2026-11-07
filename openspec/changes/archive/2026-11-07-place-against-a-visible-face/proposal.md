# Proposal

## Why

`place` places against the first neighbour of the target cell that can't be
replaced, looking below, above, then north, south, west and east. When the
player can't see that neighbour's face, `place` fails with `err out_of_reach`,
even when another neighbour's face is in plain view and a player would simply
click that one. On 26.3 Fabric, standing on flat ground:
- Filling a hole two blocks ahead (`place cobblestone ~0,-1,2`) fails: the
  hole's floor is hidden behind the grass in front of it, so the crosshair
  hits `grass_block ~0,-1,1` on its up face, though the hole's far side faces
  the player.
- A block above eye level on a column, with a block beside it
  (`place cobblestone ~2,2,0`), fails: the column's top is above the eyes, so
  the crosshair hits `stone ~2,1,0` on its west face, though the side
  neighbour's face is in view.

A tall build took a placement order worked out around this rule, and an
agent building anything hits the same failures.

## What Changes

- `place` keeps its order of neighbours, but takes the first one whose face
  toward the target cell the player can see within reach. With none in
  sight, it does as today: aims at the first and fails naming what the
  crosshair hits.
- Wherever `place` works today, it picks the same neighbour, so a torch, log,
  slab or stairs placed today comes out facing the same way.
- A target cell aboard a Sable sub-level keeps today's choice.
- The help and `docs/commands.md` say how the neighbour is chosen.

## Capabilities

### New Capabilities

None.

### Modified Capabilities
- `player-control`: `place` chooses a neighbour whose face is in sight.

## Impact

- `game/InteractCommands.java` (`place` and its help), with a sight test beside
  `seen` in `game/PlayerActions.java`.
- `tools/smoke_test.py`: steps for filling a hole, for placing above eye level
  beside a block, and for a torch that still stands on the ground with a wall
  in sight. The step for a side out of sight stays as it is.
- `docs/commands.md`: the `place` row.
- No change to the grammar, the reply or the error codes.

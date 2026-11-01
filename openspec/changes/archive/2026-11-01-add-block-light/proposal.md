# Proposal

## Why

Blockpilot has no way to read light. A play-test aboard a Sable sub-level
found parts of block models drawn nearly black while the same blocks in the
world are lit. The black parts are the ones set in from the block's faces,
such as a sprinkler's riser. The suspected cause is the light the sub-level's
plot holds for those cells, but no command can show it. The same gap affects
any lighting question: dark spots where mobs spawn, a modded block's light
emission, light that differs between client and server.

## What Changes

- `block <pos> light` adds the light stored for that cell as `sky=<0-15>` and
  `block_light=<0-15>`. By default they come from the client; with
  `side=server` they come from the integrated server's level.
- With Sable, for a plot position it also adds `world_sky=` and
  `world_block_light=`: the light at the world cell where the block is drawn,
  when that cell is loaded.
- Without `light`, `block`'s reply is unchanged.

## Capabilities

### New Capabilities

None.

### Modified Capabilities
- `game-perception`: light in `block`.
- `sable-sublevels`: the world cell's light for a plot position.

## Impact

- `game/PerceptionCommands.java` (`block`: the keyword, the fields, the help).
- README: the perception row and the Sable section.

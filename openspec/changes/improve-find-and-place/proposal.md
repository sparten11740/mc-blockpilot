# Proposal

## Why

A play-test that had to count one block's 360 pieces by their state hit
several gaps:
- `find` lists at most the 100 nearest matches, with no way to see the rest
  short of moving the player.
- No block argument takes a state: `find oak_log[axis=y]` is a syntax error,
  so counting by state took a `block` per piece.
- `place` fails with "cannot see the face" when the block it places against
  is thin, such as a carpet or a slab, while `use` at the same point works.
- `mine <pos>` misses a thin block such as a carpet or a rail: it aims at the
  middle of the cell, above the block.
- When `mine` or `place` can't see their target, the error doesn't say what
  is in the way. Here it was another part of the same large block, which
  would have made the cause plain.

Two behaviours the specs already state are missing from the help and the
README:
- Held keys, buttons and movement are released when their connection
  closes, so a `sneak on` from an earlier connection is gone by the next
  one.
- `\;` keeps a semicolon inside a step, which NBT int arrays (`[I;1,2]`) in
  `server` commands need.

## What Changes

- Block arguments take states in Minecraft's own syntax: `oak_log[axis=y]`,
  `#logs[axis=y]`, `*_stairs[half=top,facing=east]`. This works in `find`,
  `mine` by block, `sable:find` and `sable:tp`.
  - A block id with a state it lacks, or a value its state doesn't take,
    fails with `err syntax` naming what it has.
  - A tag or pattern matches only the blocks that have the state with that
    value.
- `find`'s and `sable:find`'s limit go up from 100 to 1000.
- `place` aims at the face of the supporting block's shape rather than of its
  cell, so thin and narrow blocks can be placed against.
- `mine` aims at the centre of the block's shape, as `use` does, so thin
  blocks are mined by position.
- `mine`'s "is behind another block" and `place`'s "cannot see the face" name
  what the crosshair hits instead: a block and its position, an entity, or
  nothing.
- Help and README: held input and its release in the movement and input help;
  `\;` in `help server`.

## Capabilities

### New Capabilities

None.

### Modified Capabilities
- `command-language`: the `block-state` slot type in the grammar, and block
  state filters.
- `game-perception`: `find`'s limit.
- `sable-sublevels`: `sable:find`'s limit.
- `player-control`: `place` aims at the support's shape, `mine` at the
  block's shape, and blocked-view errors name what is in the way.

## Impact

- `api/Lexicon.java`, `api/ArgType.java`, `protocol/Grammar.java`: the
  `block-state` token.
- `game/BlockMatcher.java`: state filters, with a pure parser for the bracket
  part.
- `game/PerceptionCommands.java` (`find`), `game/ActionCommands.java` (`mine`,
  `place`, the blocked-view errors, the movement help),
  `game/sable/SableCommands.java` (`sable:find`, `sable:tp`),
  `game/InputCommands.java` (the input help), `game/ServerCommands.java` (the
  `server` help), `game/Shapes.java` (a shape's face).
- Tests: the lexicon and the spec grammar, the bracket parser, a shape face
  for `place`. The smoke test gets steps for placing against a carpet and for
  finding by state.
- README: the perception, movement, input, server and Sable rows.

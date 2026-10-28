# Design

## Context

- `agl` comes from `SableCommands.heightAboveGround`. It scans down from the
  bottom of `Info.worldBounds()` at their centre to the first block with a
  collision shape.
- On the client, Sable's world bounds are the box around the plot bounds'
  eight turned corners, swept over the last tick's box.
- Sable's companion API gives a sub-level's pose, last pose, world bounds, id
  and name. It doesn't give the plot's own block bounds. `Info.plotBounds()`
  is the world bounds turned back, so it holds every block but grows while the
  sub-level is turned.
- `SubLevels.toPlot(level, id, point)` turns a world point into the plot by
  the sub-level's pose. `toWorld(level, point)` turns a plot point into the
  world when the point lies in a plot.
- The client level holds the plot's chunks, which `sable:find` already scans
  section by section through `BlockScan`.

## Goals / Non-Goals

**Goals:**
- `agl` gives the same height whichever way the sub-level is turned, and is
  cheap enough to sample every tick for a sub-level of a hundred thousand
  blocks.

**Non-Goals:**
- The least clearance under every block. Over uneven ground the block lying
  lowest need not be the one nearest the ground, and finding that one means
  reading the ground under every bottom block.
- Changing `box=` or `plot=`, which still give the bounds as Sable has them.

## Decisions

### D1. The lowest block by a dot product

A plot point's world height is affine in the point. Its gradient `up`, world
up seen in the plot, is `toPlot(position + (0,1,0)) - toPlot(position)`. The
block lying lowest is the one with the least `up · pos`, and its lowest corner
is its far side along each axis where `up` is negative. That corner, nudged a
millionth toward the block's centre so that it stays inside the plot, goes
through `toWorld`.

Alternatives: the turned bounds' bottom (today's fault); the sub-level's
position, which doesn't depend on turning but reads well above 0 when parked;
the bottom of each block's collision shape, which costs a shape lookup per
block for at most a fraction of a block. A block is anything that isn't air,
so a block without collision hanging lowest still counts.

### D2. Sections from the lowest-reaching

Within the plot bounds, every loaded section that isn't all air gets a bound:
the least `up · pos` any block in it could have, at its corner furthest down.
Sections are searched in order of that bound. The search stops at the first
section whose bound can't beat the block already found. For a sub-level of
any size this reads one to a few sections of 4096 blocks, instead of every
block.

### D3. Ground under the lowest point

The ground scan is today's, started at the lowest point instead of under the
bounds' centre. On flat ground this is the sub-level's clearance. A lowest
point inside the ground gives 0, as today.

### D4. `y=` in brief

`y=` is the world y of `Info.position()`, in tenths like the other fields,
placed before `agl` so that the two read side by side.

## Risks / Trade-offs

- [A level sub-level's bottom blocks tie, and a tiny tilt picks a different
  edge from one sample to the next] → over flat ground the height is the
  same. Over uneven ground the ground under that edge is still ground under
  the sub-level, which the spec describes as the ground under the lowest
  point.
- [A sub-level whose plot chunks the client doesn't hold] → the search finds
  no block and `agl=none`, rather than a height measured from a guess.

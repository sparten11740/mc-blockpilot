# Design

## Context

The server thread reports each block change it marks for sending through the
`ServerChunkCache.blockChanged` mixin to `BlockChanges.changed`. That method
reads the block, its block entity type and (cached per section) the sub-level,
and `Perf.blockChanged` counts them under a lock, since the summary is built on
the client thread. The hook runs once per change, so a busy build or settling
terrain calls it thousands of times a second: per-change work must stay a
couple of map operations.

## Goals / Non-Goals

**Goals:**
- Name the positions that churn, cheaply enough to leave on in every capture.
- Keep memory bounded however much changes during a capture.

**Non-Goals:**
- Exact ranking past the bound: after 65,536 positions, new positions are not
  ranked.
- The world position of a plot block at stop time; the plot position is
  stable and Blockpilot commands already take it.

## Decisions

### D1: Count positions in the same call and lock as the blocks

`BlockChanges.changed` passes the dimension and the position along with the
block, block entity and sub-level, and `Perf.blockChanged` updates both maps
under the one lock it already takes. Each position keeps its count, the block
it last changed to and its sub-level name. The key is the dimension and the
packed position (`BlockPos.asLong`), so the same coordinates in two dimensions
stay apart. An alternative, a second mixin or hook just for positions, would
read the same state twice for no gain.

### D2: A plain cap, not a heavy-hitter sketch

The map stops taking new positions at 65,536. Changes at untracked positions
add to one counter, reported as `block_change_positions_untracked`. A position
that churns shows up early and keeps its entry, so the busiest are found in
the cases this is for. At the bound the map holds about 7 MB. The alternative,
a bounded top-k algorithm such as Space-Saving, ranks correctly under any
churn, but it is more code and its counts are estimates. A `perf start` after
a large terrain update starts a fresh map anyway.

### D3: Absolute positions, plot positions as they are

The summary is saved and read later, when the player may stand elsewhere, so
positions are absolute in both the JSON (`pos` as `[x,y,z]`) and the text
overview, as `camera` reports its position. A block aboard a sub-level is
given at its plot position, which `block` and `use` accept. `dim` is given
only outside the overworld, as `status` names it, so the common case stays
short.

### D4: No option for the list length

The file always holds 50 entries, and the text overview shows the top five,
as every list does. A `top=` option would change only how much of the file the
reply repeats.

## Risks / Trade-offs

- [The cap fills with settling terrain before the churning build starts] →
  `block_change_positions_untracked` says so, and starting the capture after
  the terrain has settled avoids it.
- [A position that alternates between two blocks shows only the last] → The
  per-block counts still show both blocks.

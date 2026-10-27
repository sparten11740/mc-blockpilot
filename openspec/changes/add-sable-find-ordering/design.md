# Design

## Context

- `sable:find` scans the plot bounds, collects every match with its plot and
  world position, sorts them by world distance from the player's eyes, and
  then applies the limit. `sable:tp <sub> <what>` boards next to the first of
  the same list.
- A sub-level only moves and turns, so distances in its plot and in the
  world agree.
- `SubLevels.toWorld` turns a plot point into its world point and leaves any
  other point as it is.
- `find` already adds `count=` when its limit left matches out.

## Goals / Non-Goals

**Goals:**
- The rows an agent gets are the first of all matches in the order it asked
  for, and the reply says when there were more.

**Non-Goals:**
- Ordering for `sable:tp <sub> <what>` or for `find`. Both could take the same
  options later.
- World-axis sorting. A heading-independent "furthest aft" needs the
  sub-level's own axes, and `near=` covers world-relative questions.

## Decisions

### D1. `sort=` on the plot axes

Plot positions are fixed in the sub-level's own frame, so sorting by them
finds the same end block whatever the heading, pitch or roll. Ties on the
sorted axis keep the distance order (from `near=` or the eyes), so the result
doesn't depend on scan order.

### D2. `near=` takes a world or plot position

The position is resolved as other position arguments are (`~` relative to
the player), then passed through `SubLevels.toWorld`. A plot position becomes
its world point, and a world position stays as it is. Distances are then
measured to each match's world position, as for the eyes today.

### D3. `count=` as `find` has it

The reply carries `count=<all matches>` only when the limit left some out,
so a full list reads as before.

## Risks / Trade-offs

- [`-x` and friends as option values] → the values are fixed words that the
  registry checks at start-up; a dev-client start shows they are accepted.

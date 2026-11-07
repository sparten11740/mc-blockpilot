# Design

## Context

- `Navigator` is an `Action`: it plans with `PathSearch` over `Terrain`,
  presses movement keys each tick, replans, and fails with `no_path` or
  `stuck`. `goto`, `use` and `open`/`take` (walking to a block) build one.
- `mine` by type walks to each block with the same machinery.
- Baritone (LGPL-3.0) exposes `baritone.api.BaritoneAPI`: the primary
  `IBaritone` per player, its custom goal process (`setGoalAndPath` with a
  `GoalNear` or `GoalBlock`), its mine process (`mine(count, blocks)`), its
  pathing behaviour (`isPathing`, `cancelEverything`) and input overrides.
  Builds exist for 1.21.1 Fabric and NeoForge; none for 26.x yet.

## Goals / Non-Goals

**Goals:**
- `goto` and `mine` through Baritone when it is installed, chosen per call
  or by default, with the same answers and the same ways to stop.
- No dependency: Blockpilot loads and works the same without Baritone.

**Non-Goals:**
- Baritone's other processes (building, farming, exploring) or its chat
  commands.
- Shipping or downloading Baritone for players.
- Any Baritone code in Blockpilot.

## Decisions

### D1: A pathfinder seam

`Pathfinder` gives an `Action` that brings the player near a target
(`walk(session, target, near)`) and one that mines a number of blocks of a
kind (`mine(session, matcher, count)`). The built-in one wraps `Navigator`
and today's mining walk unchanged. Pathfinders register in a map keyed by
their `via=` name; `goto` and `mine` look up `via=`, or the config's
`pathfinder`, there, and an unknown name answers `err syntax` listing the
registered ones, so a further backend is one more registration and the
commands don't change. `use`, `open` and `take`, which walk to a block on
the way, keep the built-in `Navigator`.

### D2: Baritone through reflection

`BaritonePathfinder` finds `baritone.api.BaritoneAPI` through the class loader
that holds the mods (the game's, not Blockpilot's own, which may not see another
mod) and calls its public API by reflection, building the goal object the same
way. Polling `isPathing` can't tell arrival from failure, so the adapter
registers a game event listener on Baritone's event bus, an interface it
implements with `java.lang.reflect.Proxy`, and maps its path events: at the
goal, or the player within `near=` of the target, answers `ok`; a failed path
calculation answers `err no_path`; a path Baritone was following that its events
report as failed or abandoned answers `err stuck <pos>`, while Blockpilot's own
cancel answers `err cancelled`. No fixed time without movement counts as stuck,
as breaking or placing a block keeps the player still for a while. Cancelling
calls `cancelEverything`. Reflection keeps Baritone off the compile classpath
and out of the jar, so licences and versions stay apart; an API change in
Baritone shows up as `err unsupported` naming the method.

### D3: Inputs and interrupts

While Baritone drives, it holds the movement inputs. Real input still
reaches Blockpilot's interrupt first, which stops the script and cancels
the goal; the release after an action releases Baritone's overrides too.

## Risks / Trade-offs

- [Baritone's API changes between versions] → One adapter class, found by
  reflection, fails clearly; the smoke check runs on the Baritone build the
  dev runs fetch.
- [No Baritone for 26.x] → `via=baritone` answers `err unsupported` there
  until a build exists; the check skips.
- [Baritone breaks and places blocks] → That is the point of choosing it;
  the built-in pathfinder, which never does, stays the default.

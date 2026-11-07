# Design

## Context

- `Navigator` is an `Action`: it plans with `PathSearch` over `Terrain`,
  presses movement keys each tick, replans, and fails with `no_path` or
  `stuck`. `goto`, `use` and `open`/`take` (walking to a block) build one.
- `mine` by type walks to each block with the same machinery.
- Baritone (LGPL-3.0) exposes `baritone.api.BaritoneAPI`: the primary
  `IBaritone` per player, its custom goal process (`setGoalAndPath` with a
  `GoalNear` or `GoalBlock`), its pathing behaviour (`isPathing`,
  `cancelEverything`), its event bus and input overrides.
  cabaletta publishes builds for NeoForge and Fabric on 1.21.1 (v1.11.3),
  26.1 (v1.18.0), 26.2 (v1.19.0) and 26.3 (v1.20.0) on GitHub, with
  checksums; the vanilla agent targets have no loader to load a mod.

## Goals / Non-Goals

**Goals:**
- `goto` and `mine` through Baritone whenever it is installed, with no
  option to give, the same answers and the same ways to stop.
- No dependency: Blockpilot loads and works the same without Baritone.

**Non-Goals:**
- Baritone's other processes (its own mining, building, farming,
  exploring) or its chat commands; `mine` keeps its own choice of blocks.
- Shipping or downloading Baritone for players.
- Any Baritone code in Blockpilot.

## Decisions

### D1: Pathfinders behind one adapter port

`goto` and `mine` depend only on a `Pathfinder` port, never on a backend:
`name()`, `available()` and an `Action` that brings the player near a target
(`walk(session, target, near)`). Each backend is an adapter behind that
port, in a class of its own: `BuiltInPathfinder` wraps `Navigator` unchanged
and is always available, and `BaritonePathfinder` keeps everything about
Baritone, its reflection included, to itself. `mine` by type keeps choosing
and digging blocks itself and walks into reach of each through the port, so
with Baritone it digs its way to blocks the built-in walk can't reach.
`Pathfinders` holds the adapters in order of preference, Baritone's first,
and picks the first available one at each call, as Baritone is only
available once its class is loaded and a world gives it a player; an adapter
other than the built-in one adds its name to the reply as `by=`, so the
built-in pathfinder's replies keep their shape. Swapping in another
pathfinding mod is one adapter class and one line in that order, with no
change to the commands or to the other adapters. `use`, `open` and `take`,
which walk a few blocks to a block on the way, keep the built-in
`Navigator`.

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
- [The vanilla agent targets can't load Baritone] → They keep the built-in
  pathfinder; the check skips there.
- [With Baritone installed, `goto` may break and place blocks] → That is
  what installing it asks for; Baritone's own `allowBreak` and `allowPlace`
  settings turn it off, the help says so, and `by=` shows which pathfinder
  walked.
- [Tests of the built-in pathfinder in a game with Baritone] → The regular
  runs have no Baritone; only the Baritone run configuration loads it.

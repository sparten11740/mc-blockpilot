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
with Baritone it digs its way to blocks the built-in walk can't reach. A
walk to 3 blocks may end outside the wall the block is behind, so when the
crosshair still can't hit a block in reach, `mine` walks closer once, to 1.5
blocks, through the port before passing over it: Baritone digs through, and
the built-in walk, which never digs, finds no path there, and the block is
passed over as before. The walk closer has no time limit of its own: digging
through a thick wall takes as long as it takes, and it ends when the
pathfinder answers or `stop` or real input ends the script.
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
mod), trying the thread's context class loader, then Minecraft's, then its own;
the dev runs on 1.21.1 and 26.3 found it so on NeoForge and Fabric, and the
proxied listener received path events on both. It calls the public API by
reflection, building the goal object the same way: `GoalBlock` without `near=`,
else `GoalNear` with `near=` rounded down, so wherever Baritone's goal ends
counts as near enough. A moving target sets the goal again once it is more than
2 blocks from it.

The player within `near=` of the target answers `ok`. Polling Baritone can't
tell a failure from a walk still under way, so the adapter registers a game
event listener on Baritone's event bus, an interface it implements with
`java.lang.reflect.Proxy`. The 1.21.1 dev run showed how Baritone reports a
goal it can't reach, sealed in bedrock: `CALC_STARTED`,
`CALC_FINISHED_NOW_EXECUTING` for a partial path towards it,
`NEXT_CALC_FAILED` twice as it walks, then `CALC_FAILED` at the wall. It
tries again after `NEXT_CALC_FAILED`, walking as near as it can, so only
`CALC_FAILED`, a calculation from where the player stands that finds
nothing, answers `err no_path`. Baritone's custom goal process going idle
short of the goal, after 10 ticks to take the goal on, answers `err stuck
<pos>`, while Blockpilot's own cancel answers `err cancelled`. No fixed time
without movement counts as stuck, as breaking or placing a block keeps the
player still for a while. Cancelling calls `cancelEverything`. Reflection
keeps Baritone off the compile classpath and out of the jar, so licences and
versions stay apart; an API change in Baritone shows up as `err unsupported`
naming the method.

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
- [On 26.x, Baritone's own threads outlive `quit`, so the game's shutdown
  watchdog ends it with a crash report] → Baritone's doing, seen in a dev run
  that never walked through it; the Baritone runs don't check closing, and
  the regular runs do.
- [Loom's remapped copy of a mod no longer declares its nested jars] → On
  1.21.1 the Fabric dev run takes Baritone's nested `nether-pathfinder` from
  the checked jar as a plain library.

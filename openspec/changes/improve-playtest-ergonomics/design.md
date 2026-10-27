# Design

## Context

Each item is small and sits in its own command, so the change is cross-cutting
rather than deep. Relevant current state:

- `find` already sorts every match (blocks, entities, drops) before cutting the
  list to its limit, so the total is known without a second scan.
- `mine` holds attack until the block's state changes. Nothing checks whether
  holding it can ever break the block.
- `goto <pos>` hands the cell to the path search, which only fails after
  expanding up to 60000 cells, with no hint of why.
- `PathSearch.standable` already decides whether the player can be in a cell,
  from the yes/no questions of `Terrain`; `PathSearchTest` drives it with a
  fake grid.
- A command's forms are tried in order against all of a call's tokens.
- The CLI's stderr summary (`Results.summary`) counts errors and skips;
  `ResultsTest` covers it.

## Goals / Non-Goals

**Goals:**
- Each new error or field comes from what the game or the command already
  knows; no new scans, timers or waits.
- The `goto` check and the path search can't disagree about a cell.

**Non-Goals:**
- Telling `mine` that a mod reverted a broken block on the server; the client
  sees the break, and `mine` counts it.
- Checking `goto`'s target again while walking; the path search covers changes
  on the way.

## Decisions

### D1. `find` counts from the lists it already sorts

Each branch has the full sorted list before the limit cuts it. When that list
is longer than the limit, the result gets `count=<size>`, next to `radius=`
when both apply. *Alternative:* always report `count`. Rejected: every reply
would grow by a field that only matters when rows are missing. `help find`
gets `limit default 5, max 100` and `count` is described as "matches in all,
when the limit left some out".

### D2. `mine` asks the game whether digging makes progress

On the first dig tick of a block, after the tool is picked, `mine` fails with
the new code `unbreakable` when the player isn't in creative and either
`state.getDestroyProgress(player, level, pos)` is 0 or less, or
`player.blockActionRestricted(level, pos, mode)` is true. That progress is the
same per-tick share the game itself adds while attack is held, so 0 means the
block can never break, whatever a mod changed. The per-block form leaves such
blocks out of its search, so a floor of bedrock costs one scan rather than a
tick per block. When the scan saw matches but none it can break, it fails
`unbreakable` with the block. *Alternative:* watch the client's crack
progress for a few ticks. Rejected: slower to answer, and that progress is
private to the game mode.

### D3. A second form for `key`

`key` keeps `<down|up|press> <key> [Nt]` and gains a second form
`<key> [Nt]`, read as `press`. Forms are tried in order, so `key up w` still
lets go of `w`, while `key up` alone only fits the second form and presses the
up arrow. Until now `key up` alone was a syntax error, so no script relied on
it.

### D4. `goto` asks the path search why a cell fails

`PathSearch` gets a method that returns why a cell can't be stood in, as an
enum (`UNLOADED`, `FILLED`, `NO_HEADROOM`, `DANGEROUS`, `NO_FLOOR`), or null
when it can. `standable` is written on top of it, so both use the same tests.
`navigate` runs it before building the `Navigator`, only for a position target
without `near=`; an entity moves, and `near=` accepts any cell in range. The
error text names the blocks from the level, with positions as replies print
them: `no_path stone fills ~2,-1,0`, with `; stand at ~2,0,0` when the cell
above passes the same check.
*Alternative:* move the check into `Navigator`'s first tick. Rejected: it
would first spend a tick on a search that can't succeed, and the error would
arrive after the action started.

### D5. Camera: `off` and `unloaded`

`camera`'s second form takes `reset` or `off`, and the action resets on
anything but `at`. When `Sides.loaded(level, cell)` is false, the reply
carries `unloaded` instead of `block` and `inside`. A missing chunk reads as
air, which would be wrong.

### D6. CLI: seconds waited and step numbers

The two timeout messages add the wait in seconds, formatted as the quit
timeout already is: `game not reachable after 300s: …` and `timed out after
60s waiting for a world (…)`. Without `--wait-ready` the CLI doesn't wait and
the message stays as it is. `Results.summary` lists the 1-based positions of
the failed results: `1 err (step 3), 2 skip (nothing after the error ran)`,
or `2 err (steps 3, 5)`. A script refused for a syntax error keeps its own
wording, with the step added.

### D7. Help notes

`help sable:list`'s `size` and `help sable:info`'s `plot` say that they are
the world bounds turned back into the plot and grow while the sub-level is
turned. `help nbt` says that `<pos>` may be a plot position. These are text
only.

## Risks / Trade-offs

- [A mod breaks its block through another route than digging, so progress is
  0 but the block can go] → `unbreakable` is still right for `mine`, which
  only digs; the error names the block, so the agent can use the mod's way.
- [`goto` refuses a target the search could reach with `near=`] → the error
  only applies without `near=`, and the cell above is named when it works.
- [The stderr summary's wording changes] → scripts read the exit code, which
  doesn't change; stderr is for people and agents reading it.

## Migration Plan

None. The change only adds replies, errors and forms. It modifies none of
the requirements that the three finished, unarchived changes modify, so it
can be archived before or after them.

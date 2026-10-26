# Proposal

## Why

In a day of play-testing a mod with Sable ships, several commands acted
somewhere other than where the agent meant, and their replies didn't say so.
`server` counted `~`, `@p` and `distance=` from the world spawn. A `use`
aimed at a block hidden behind another clicked the block in front. `sable:tp`
put the player on the outside of a hull. `find` cut a 400-block radius to 128.
Each one cost a detour or led to a wrong conclusion, so the commands should
act where they are pointed, or fail or say what they did instead.

## What Changes

- **BREAKING** `server` without `as=player` runs from the player's position,
  rotation and dimension instead of the overworld's spawn, still as the
  server's own source at permission 4. Without a player it runs at the spawn
  as before. `as=player` still runs the command as the player.
- **BREAKING** `use <pos>` on a world block and `use e<id>` fail with `err
  obstructed` when something else is under the crosshair, as `use` at a plot
  position already does. `use … any` still clicks whatever the crosshair ends
  on, and when that is not the target its reply carries
  `off_target=<target>`.
- `use … sneak` with an item in either hand reports `sneak_item=<id>`: the
  game then skips the block's own use and uses the item.
- `sable:tp` prefers a free spot from which the block is in sight, and
  reports `dist` from the block, plus `hidden` when no free spot sees it.
- `sable:tp <sub-level> <block|#tag|*pattern>` boards next to the matching
  block aboard nearest the player, so no plot position has to be carried from
  one command to the next.
- `find` reports `radius=<max>` when asked for more than its maximum, and its
  help says that it searches a cube and that blocks aboard a sub-level are
  found by `sable:find`.
- `since=` on `sounds`, `messages` and `particles` also takes a duration:
  `since=60t` covers the last 60 ticks.
- A single `screenshot` reports the tick of the frame it captured, as burst
  frames already do.
- **BREAKING** `blocks … grid` prints the palette and one palette key per
  cell instead of full block ids, so long modded ids don't stretch the grid.

Out of scope: variables in scripts, keeping a sub-level that flies away
loaded, and screenshot brightness (`option gamma` already exists).

## Capabilities

### New Capabilities

None.

### Modified Capabilities
- `server-bridge`: where `server` runs a command by default.
- `player-control`: `use` checks its target everywhere, `any` reports a click
  that went elsewhere, and a sneaking `use` names the item that took it.
- `sable-sublevels`: `sable:tp` picks a spot in sight of the block, reports
  its distance, and takes a sub-level and a block to look for.
- `game-perception`: `find` reports a cut radius, a single screenshot its
  tick, and the grid dump uses palette keys.
- `client-observation`: `since=` takes a duration.

## Impact

- **Code:** `ServerCommands` (default source), `ActionCommands` (`use`),
  `SableCommands` (`sable:tp`), `PerceptionCommands` (`find`, `blocks`),
  `ObservationCommands` (`since=`, `screenshot`).
- **Docs:** README command table and Sable section; `help` texts.
- **Testing:** unit tests where the logic is pure; in-game checks on the
  1.21.1 NeoForge dev client with Sable; the smoke test on all four jars.

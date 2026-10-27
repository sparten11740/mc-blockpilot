# Proposal

## Why

A night of play-testing a mod with large Sable ships turned up commands that
cost a detour without being wrong. `mine` on a block that can't be broken
holds attack until its timeout. `goto` to a cell the player can't stand in
searches until `no_path`. `find` lists `count` in its help but never reports
it. Small mismatches between what an agent tries and what a command accepts
(`key escape`, `camera off`) fail as syntax errors. A screenshot comes out
narrower than asked without a word, and nothing tells when the view has
finished loading after the camera moves. The CLI's stderr doesn't say how
long it waited or which step failed. Each of these makes an agent guess,
retry or wait, where the command could say at once what happened.

## What Changes

- `find` reports `count=<n>`, the number of matches, when its limit left some
  out, and its help says the limit is at most 100.
- `screenshot` reports `width=<n>` when the image is narrower than the width
  asked for (the cap at 1280, a narrower frame or crop, a sheet's tiles), and
  its help gives the cap.
- `mine` fails at once with `err unbreakable <block> <pos>` when the player
  can't break the block (no dig progress: bedrock in survival, adventure mode,
  a block a mod makes unbreakable), instead of waiting for its timeout. Mining
  by block type passes such blocks over, and fails the same way when it finds
  only blocks it can't break.
- `key <key> [Nt]` without `down`, `up` or `press` presses the key.
- `goto <pos>` fails at once with `err no_path` and the reason when the player
  can't stand in the target cell (a block fills it, no room for the head,
  nothing to stand on, an unloaded chunk). When the target is a block with
  room above it, the error names the cell above.
- `camera off` is accepted as `camera reset`.
- `camera at` reports `unloaded` instead of the block when the camera sits in
  a chunk the client hasn't loaded, which renders as empty sky.
- A new `settle [timeout=]` waits until the chunks around the player have
  arrived and those in view have compiled, as `option` then does after a
  change too, for use after `camera at`, a teleport or anything else that
  brings new chunks into view.
- The CLI's timeout message says how long it waited for the game, and its
  stderr summary of failed steps names their step numbers.
- `help sable:list` and `help sable:info` say that a sub-level's size and plot
  bounds grow while it is turned, and `help nbt` that it reads blocks at plot
  positions.

Out of scope: the bug fixes already committed for `sable:find side=server`,
the `sable:find` scan limit, `mine` and `place` aboard, and `goto`'s jumps in
creative.

## Capabilities

### New Capabilities

None.

### Modified Capabilities
- `game-perception`: `find` reports how many matched when its limit cut the
  list, and `screenshot` the width it captured at when narrower than asked.
- `player-control`: `mine` fails on unbreakable blocks, `key` presses by
  default, and `goto` fails at once on a target the player can't stand in.
- `client-observation`: `camera off`, the camera's reply flags an unloaded
  chunk, and `settle` waits for the view to finish loading.
- `dev-integration`: the CLI's timeout message and its summary of failed
  steps.
- `sable-sublevels`: listing and info explain why size and plot bounds grow
  while a sub-level is turned.

## Impact

- **Code:** `PerceptionCommands` (`find`), `ActionCommands` (`mine`, `goto`),
  `PathSearch` (why a cell can't be stood in), `InputCommands` (`key`),
  `ObservationCommands` (`camera`, `screenshot`), `OptionCommands` (its wait
  moves to a new `Settle` class with the `settle` command), `ShellClient` and
  `Results` (CLI), `SableCommands` and `ServerCommands` (help), `Errors` (new
  code `unbreakable`).
- **Docs:** README command table, CLI section and Sable section; `help` texts.
- **Testing:** unit tests for `PathSearch`, `Results` and the CLI timeout
  message; in-game checks on the 1.21.1 NeoForge dev client; the smoke test
  on all four jars.

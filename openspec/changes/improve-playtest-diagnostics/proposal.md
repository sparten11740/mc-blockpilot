# Proposal

## Why

A play-test of a mod with flying Sable ships ran without blockers, but three
gaps cost the agent detours. After a world load in which the client missed a
sub-level the server had, `sable:list` said nothing about it. The docs didn't
say which hit point `use … at` sends to the server, which matters to blocks
that read where they were touched. And when the client failed before any mod
loaded, the CLI's `--wait-ready` waited out its whole time.

## What Changes

- `sable:list side=server` lists the integrated server's sub-levels, as
  `sable:find side=server` reads the server's copy of a plot. In singleplayer
  with cheats on, the client's own list adds `server_only=<n>` when the server
  holds sub-levels within the radius that the client doesn't.
- `use` reports the crosshair hit the click was sent with, read on the tick
  the game handled the click, and `help use` and the README say that this is
  the hit the server gets: where the aimed look ray meets a block's shape.
- `--wait-ready` stops as soon as a crash report appears in the game
  directory while it waits, with exit code 2 and the report's path.
- The README notes that a client started while the display sleeps fails
  before any mod loads, so nothing of Blockpilot's can report it.

## Capabilities

### New Capabilities

None.

### Modified Capabilities
- `sable-sublevels`: listing the integrated server's sub-levels, and a note
  when the client misses some.
- `player-control`: the hit `use` reports is the one the click was sent with.
- `dev-integration`: the readiness wait stops at a crash report.

## Impact

- **Code:** `SableCommands` (`sable:list` side and note), `ActionCommands`
  (when `use` reads its hit, `help use`), the CLI's readiness wait.
- **Docs:** README command table, Sable section and gotchas.
- **Testing:** a CLI unit test for the crash report; in-game checks on the
  1.21.1 NeoForge dev client with Sable for the list and the hit.

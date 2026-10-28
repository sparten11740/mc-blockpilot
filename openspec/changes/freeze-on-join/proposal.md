# Proposal

## Why

A save/reload test needs to read the world as it was saved, but the first
reading after a reload always comes too late. The integrated server starts
ticking while the client is still loading terrain, and `join` answers only
once loading is done. A reload measured twice gave its first `tick query`
29 and 30 ticks after the time in level.dat. By then anything in motion has
moved: a sub-level climbing at speed was 45 blocks higher. The freeze state
isn't saved with the world, and Blockpilot drops its own freeze on leave, so
freezing before saving doesn't help. A test also can't tell, without
reading level.dat, which game time a world was saved at.

## What Changes

- `join world tick=frozen <folder>` freezes the integrated server's ticking
  as it starts, before its first tick, so the first reading after the join
  shows the world as saved. `tick unfreeze` or `tick step <n>` lets it go on.
  The freeze counts as Blockpilot's, so leaving drops it as it drops
  `tick freeze`. `join server` with `tick=frozen` fails, since Blockpilot
  can't freeze a remote server.
- `leave` and `quit` answer with `saved_at=t<tick>` when they leave a
  singleplayer world: the game time the world was saved at.
- The README notes that while the server is frozen, Sable sends its
  sub-levels to a player who has just joined only once a tick runs.

## Capabilities

### New Capabilities

None.

### Modified Capabilities
- `tick-control`: joining a world with its ticking frozen from the start.
- `game-session`: `leave` and `quit` report the game time a singleplayer
  world was saved at.

## Impact

- `platform/ClientPlatform.java` and both loader entry points: a new
  server-started hook.
- `game/SessionCommands.java` (`join`, `leave`, `quit`),
  `game/ServerCommands.java` (the freeze at start), `game/BlockpilotClient.java`
  (forwarding the server hooks).
- README: the session and integrated rows of the command table, and the With
  Sable section.

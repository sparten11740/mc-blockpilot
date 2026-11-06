# Proposal

## Why

A placed camera can only film what the integrated server loads and sends
around the player. Beyond the player's view distance the camera shows sky
and `camera at` answers `unloaded`; a mob farther from the player than its
tracking range never reaches the client; and the sounds, particles and
explosions the server sends only to players near them never play, so
`playsound` at a distant set is silent or, with a minimum volume, moved next
to the player. Filming a set anywhere else means teleporting the player
there first, into or beside the shot, and server commands at the set fail
with `That position is not loaded` until the player is close.

## What Changes

- `camera at <pos> … away`: while that camera is placed, the integrated
  server treats the camera as where the player is for the world it loads,
  ticks and sends. The chunks within the view distance of the camera load
  and reach the client, those near it tick and may spawn mobs, entities
  within tracking range of the camera are sent, and sounds, particles and
  explosions within range of the camera play, `playsound` included.
- Farther from the camera than the view distance, the player's own
  surroundings aren't sent, and the player stands still until the camera
  resets.
- It ends with `camera reset` or `off`, a new `camera at` without `away`,
  leaving the world, agent control off, and the player changing dimension.
  The server then sends the chunks around the player again.
- `settle` waits on the chunks around such a camera while it is placed.
- It needs the integrated server with commands allowed, like `server`:
  `err no_server` and `err no_cheats` otherwise.
- The player's own entity keeps ticking on the server meanwhile, so what it
  is given or loses still reaches its inventory.
- The help and `docs/commands.md` say what `away` does and what it leaves to
  the player's place: how near a player mobs spawn and despawn, and anything
  else that asks for the nearest player.
- `help grammar`, `docs/grammar.ebnf` and its diagrams show `away`.

## Capabilities

### New Capabilities

None.

### Modified Capabilities
- `client-observation`: `camera at` takes `away`, which moves the world the
  integrated server loads and sends from the player to the camera; `settle`
  waits on the chunks around such a camera.

## Impact

- `game/observe/ObservationCommands.java` (`camera` and its help) and
  `game/observe/Settle.java`.
- A server-side record of where each player's camera is away, read on the
  server thread, and mixins in the 1.21.1 and 26.1 trees: `ChunkMap` (the
  tracking centre, the player's section for loading and ticking, the chunk
  ticking range, entity tracking), `PlayerList.broadcast`,
  `ServerLevel.sendParticles` and `explode`, and `PlaySoundCommand`.
- Possibly a client mixin that keeps the player still where its chunk is
  missing, on the versions that don't already (see design D4).
- `tools/smoke_test.py`: a check that films a set 1000 blocks from the
  player.
- `docs/grammar.ebnf` and `docs/grammar.xhtml`, regenerated.
- `docs/commands.md`: the `camera` and `settle` rows.
- No change to the reply of `camera at` without `away`, nor to remote
  servers.

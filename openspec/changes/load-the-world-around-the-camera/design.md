# Design

## Context

- `camera at` is client-only: `CameraMixin` sets the main camera's position
  and angles in `Camera.setup` on 1.21.1 and `Camera.update` on 26.x, from a
  `CameraControl.Placement` whose points may follow an entity or a
  sub-level. The sound listener already follows it, since
  `Minecraft.runTick` hands the main camera to `SoundManager.updateSource`
  every frame.
- On both 1.21.1 and 26.x, the integrated server places everything it does
  for a player by that player's position:
  - `ChunkMap.updateChunkTracking` centres the player's
    `ChunkTrackingView` on `player.chunkPosition()`; `applyChunkTrackingView`
    sends `ClientboundSetChunkCacheCenterPacket` and the chunks entering and
    leaving the view.
  - `ChunkMap.move` and `updatePlayerPos` hand `SectionPos.of(player)` to
    `DistanceManager.addPlayer`/`removePlayer`, which hold the chunks around
    that section loaded (view distance) and ticking (simulation distance).
  - `ChunkMap.playerIsCloseEnoughForSpawning`, a player within 128 blocks,
    decides on 1.21.1 which chunks get random ticks and may spawn, both in
    one check in `ServerChunkCache.tickChunks`. On 26.x it decides only which
    chunks may spawn; random ticks go to the entity-ticking chunks
    (`ChunkMap.forEachBlockTickingChunk`), which the player's tickets set.
  - `ServerLevel` ticks an entity only in entity-ticking range. On 26.x a
    `ServerPlayer` is ticked wherever it is; on 1.21.1 it isn't.
  - `ChunkMap.TrackedEntity.updatePlayer` sends an entity to the player when
    it is within the entity's range of `player.position()` and in a tracked
    chunk.
  - `PlayerList.broadcast` (sounds, level events), `ServerLevel.sendParticles`
    (32 or 512 blocks), `ServerLevel.explode` and `PlaySoundCommand` measure
    from the player.
- The client keeps chunks only within its view distance of the centre the
  server last sent, and drops the rest. One client can't hold the player's
  surroundings and a set farther away than that at once.
- `LocalPlayer.tick` on 1.21.1 runs only while the player's chunk is loaded,
  so a player whose chunk is gone stands still. On 26.x it runs once the
  client has loaded, and whether the player then falls is to be seen.
- `settle` counts the chunks within the view distance of the player.

## Goals / Non-Goals

**Goals:**
- Film any place in the world from the player's seat, with the terrain,
  entities, sounds and particles there, without moving the player.
- Leave the server as it was the moment the camera resets.

**Non-Goals:**
- Remote servers, where the server decides what a client gets.
- Holding both the player's surroundings and the set: the client can't.
- How near a player mobs spawn, despawning, mob targeting and other rules
  that ask for the nearest player; they still go by where the player is.
- Moving the camera smoothly; that is `add-camera-moves`, which can combine
  with `away`.

## Decisions

### D1: The player's place moves to the camera

While a camera is away, the server measures each of the points above from
the camera instead of the player, through one lookup, `presence(player)`:
the camera's position when that player's camera is away in the player's
dimension, else the player's own. It takes nothing from the server that the
player didn't have, and gives it all back at the reset by measuring from the
player again.

Alternatives:
- Add tickets around the camera and leave tracking on the player: the chunks
  load, but the client never gets them, nor the entities there.
- Teleport the player to the camera: the player stands in the shot, touches
  what is there and draws mobs.
- A second, fake player at the camera: the player list, saves, sleep and
  spawn counts would all see it.

### D2: The hook points, per source tree

The 1.21.1 and 26.1 trees each get mixins that replace the player's position
with `presence(player)` in: `ChunkMap.updateChunkTracking`, `move` and
`updatePlayerPos` (as `SectionPos`/`ChunkPos`),
`playerIsCloseEnoughForSpawning`, `TrackedEntity.updatePlayer`,
`PlayerList.broadcast`,
`ServerLevel.sendParticles` with a player, `ServerLevel.explode` and
`PlaySoundCommand.playSound`. NeoForge's patch around
`anyPlayerCloseEnoughForSpawning` keeps calling
`playerIsCloseEnoughForSpawning`, so the same hook serves it. Each is a
redirect or local modification of the position read, not an overwrite.

`playerIsCloseEnoughForSpawning` is hooked on both versions, so both agree:
the set's chunks get random ticks (on 26.x through the moved tickets alone)
and may spawn. The spawner's own rules still measure from the player: a
monster more than 128 blocks from it would despawn at once and so never
spawns at a distant set; animals can.

### D3: The server re-centres every tick

A camera on an entity or a sub-level moves, and a player standing still
sends no movement for the server to react to. While a camera is away, the
server thread calls `ServerChunkCache.move(player)` once a tick for that
player, so the tracking, the loading and the entities follow the camera's
chunk and position. The client writes the camera's absolute position into
the record each tick; the server only reads it. Placing and resetting call
`move` at once, so neither waits a tick.

### D4: The player stands still

On 1.21.1 the player stops when its chunk leaves the client, as vanilla
does after a teleport into chunks still to come. On 26.x the task verifies
the same; where the player would fall instead, a client mixin skips
`LocalPlayer.tick` while a camera is away and the player's chunk is missing,
as 1.21.1 does. Agent actions that move the player make no progress meanwhile
and time out; the help says to reset first.

### D5: Gate and end

`away` checks the gate `server` uses (`err no_server`, `err no_cheats`)
before anything changes. The record is cleared, and `move` called, on every
path that resets the camera today, on a new `camera at` without `away`, and
when the player changes dimension, which the server sees first and the
client follows.

`away` is kept beside the placement, not in it: the `camera` command and the
resets set and clear it, `CameraControl.place` doesn't. A `camera move` from
`add-camera-moves`, which places mid-move and at its end through `place`,
therefore keeps the camera away, and the server follows it each tick (D3).
Whichever of the two changes lands second checks that its code keeps to
this.

### D6: The player keeps ticking

On 1.21.1, moving the player's tickets would leave its own chunk out of
entity-ticking range, and the server would stop ticking the player: no
inventory sync, no game-mode tick. There the change keeps a ticket of its own,
made with `TicketType.create` (no registry on 1.21.1), holding the player's
chunk entity-ticking while the camera is away. 26.x ticks players anywhere
and needs none.

### D7: Settle follows the camera

While a camera is away, `settle` takes the camera's chunk as its centre
instead of the player's; that is where the server now centres the client's
chunks.

### D8: The client draws around the camera on 1.21.1

1.21.1's `LevelRenderer.setupRender` centres the sections it draws on the
player's position, where 26.x centres them on the camera, so on 1.21.1 a
camera 1000 blocks away showed only sky though its chunks had arrived. A
1.21.1 mixin hands it the camera's position while a camera is away.

### D9: Sable's sub-levels stay with the player

Sable sends sub-levels to a client by where its player is, through its own
tracking rather than the chunk map's, so a camera away doesn't show them;
the help and `docs/sable.md` say so.

## Risks / Trade-offs

- [A summoned monster at the set despawns at once, as it is more than 128
  blocks from any player] → The help says to summon such actors with
  `{PersistenceRequired:1b}`; animals and named mobs stay anyway.
- [A mod that sends its own data by the player's distance, such as Sable's
  sub-levels, may not follow the camera] → The task checks a sub-level 1000
  blocks off; if it doesn't arrive, the help names the limit and a
  follow-up looks at that mod's tracking.
- [Server internals differ between the trees, and a future version can move
  a check elsewhere] → One mixin per hook point and tree, each a narrow
  redirect, and a smoke check that fails on every target the day one stops
  applying.
- [Re-centring calls `ChunkMap.move`, which goes through every tracked
  entity] → Vanilla does the same for each moving player on every movement
  packet; it is once a tick for one player.
- [The player's chunks unload on the server while the player stands in
  them] → Players aren't saved with chunks, and the client's player waits
  for its chunk before it moves again after the reset. On 1.21.1 one chunk
  stays entity-ticking for the player (D6); a task checks that an item given
  meanwhile reaches the inventory on both versions.

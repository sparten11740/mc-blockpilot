# Design

## Context

- `ConnectionMixin` counts each packet the client's connection receives,
  at `Connection.channelRead0`. A bundle arrives there as one
  `BundlePacket`, whose `subPackets()` the client's listener then handles one
  by one. Both versions have `subPackets()`.
- A chunk becomes full in `ChunkStatusTasks.full`, on the server thread. A
  chunk read from disk arrives there wrapped as an `ImposterProtoChunk`, a
  new one as a plain proto chunk. NeoForge fires `ChunkEvent.Load` there with
  `isNewChunk()`. Fabric fires `CHUNK_LOAD` for every chunk and also
  `CHUNK_GENERATE` for new ones.
- `PlayerChunkSender.sendChunk` sends one chunk to one player, and
  `PersistentEntitySectionManager.addEntity` adds every entity to a server
  level, spawned or loaded. Both have these names and arguments in both
  versions.
- `Perf.serverTick` gets the previous tick's time at the end-of-tick hook,
  labelled with the game tick that hook saw a tick earlier.

## Goals / Non-Goals

**Goals:**
- What came in bundles is counted, and every slow tick says what the server
  did.

**Non-Goals:**
- Timing chunk work per step. Counts are enough to tell generation from
  loading or sending.

## Decisions

### D1. Bundle contents

When the packet is a `BundlePacket`, `ConnectionMixin` also counts each of
its sub-packets by type. The bundle itself still counts as `bundle`, so the
total of packets that crossed the connection stays as before.

### D2. Counts per server tick

`Perf` keeps four counters on the server thread: chunks loaded (any), chunks
generated, chunks sent and entities added. Hooks add to them while a capture
runs:
- chunk loads and generations through new platform hooks, from
  `ChunkEvent.Load` on NeoForge and `CHUNK_LOAD`/`CHUNK_GENERATE` on Fabric;
- a mixin at the head of `PlayerChunkSender.sendChunk`;
- a mixin at the head of `PersistentEntitySectionManager.addEntity`.

At the end-of-tick hook the counters since the previous hook belong to the
tick that just ended. Since the hook records the previous tick's time, it
keeps the counts one hook longer and records them with that time. Chunks read
from disk are loads minus generations.

### D3. Where the counts show

Spikes become `{tick, ms, chunks_generated?, chunks_loaded?, chunks_sent?,
entities_added?}`, with zero counts left out. The file's timeline adds four
arrays of the same length. The summary adds `server.chunks` with
`{generated, loaded, sent}` and `server.entities_added`, and the text
overview's spike rows show the counts after the time.

## Risks / Trade-offs

- [`addEntity` runs for every entity loaded with a chunk] → one counter add
  while a capture runs. That is the count wanted, since loading entities
  costs the tick too.
- [Counts land on the tick whose end-of-tick hook follows them] → work
  between two ticks counts toward the next one. That is the tick it delays.

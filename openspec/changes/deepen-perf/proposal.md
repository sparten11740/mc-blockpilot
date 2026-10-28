# Proposal

## Why

A capture points at a cost but often not at its cause, so agents fall back on
temporary mixins and grepping jars:

- Server tick time comes as average, p95 and max, so a spike every few
  seconds hides between them, and nothing says when it happened.
- The network list counts block update and block entity data packets, but not
  which blocks changed. Finding that a machine's parts change a thousand times
  a second took a temporary mixin on the server's chunk cache.
- The particle list names the types that cost frame time, but not the mod or
  the code that spawns them. Finding the spawner took searching mod jars for
  the particle's id.

## What Changes

- Server tick time adds p99 and the ticks over 50 ms ("spikes"), each with
  the game tick it ended on, longest first. The saved file also holds every
  tick's time by game tick. The game tick is the same clock as the `t<n>` on
  `sample` rows, so spikes line up with what was sampled.
- With an integrated server, the summary counts the block changes the server
  sends to clients, per block, with the block entity type when the block has
  one and, with Sable, the sub-level the block is aboard. They are ranked by
  changes per second.
- For each particle type, the summary names where a sample of its particles
  came from: the mod and class of the code that spawned it, or `server` for
  particles the server sent. In singleplayer it also names the mod and class
  that sent those on the server.

## Capabilities

### New Capabilities

None.

### Modified Capabilities
- `performance-capture`: p99 and spikes of server ticks, a per-tick
  timeline in the file, block changes by block, and particle sources.

## Impact

- `game/perf/Perf.java` and `PerfCommands.java` (summary, text overview).
- Mixins for both Minecraft versions: the server chunk cache's block changes,
  the client's particle creation (existing), and the server's particle
  sending.
- `platform`: the mod a class comes from, on NeoForge and Fabric.
- README: the performance row.

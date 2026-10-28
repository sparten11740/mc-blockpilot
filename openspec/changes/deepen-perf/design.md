# Design

## Context

- `Perf.serverTick` gets each server tick's time from the server's own tally
  at the end-of-tick hook. It reads the previous tick's slot, since a loader
  may fire the hook before the tally. The summary keeps every tick's time,
  then reports average, p95 and max.
- The game tick replies report is the integrated server's overworld game
  time in singleplayer. Server-side queries in a sample read it at the end of
  a server tick.
- `ServerChunkCache.blockChanged(BlockPos)` is where a level marks a changed
  block to send to the players tracking its chunk. The chunk holder then sends
  a block update or a section update, and the block entity's data for blocks
  that have one and send any. It has that name in 1.21.1 and 26.1.
- `ParticleEngineMixin` hooks `ParticleEngine.createParticle`, which client
  spawns and particle packets go through. It tags each particle with its type.
- `ServerLevel.sendParticles` is how server code sends particles to players.
  Its overloads differ between 1.21.1 and 26.1.
- Instrumented code checks `Perf.active` and does nothing more while no
  capture runs.

## Goals / Non-Goals

**Goals:**
- From one capture: when the server's slow ticks happened, which blocks keep
  changing and where, and which code spawns each particle type.

**Non-Goals:**
- Block changes on a remote server. Only the integrated server can be hooked.
- Exact particle counts per source. Sampling keeps the stack walks cheap, so
  shares are estimates.

## Decisions

### D1. p99, spikes and a timeline

The summary's `server.mspt` gains `p99`. `server.spikes` lists the ticks over
50 ms, the time a tick has at 20 TPS, longest first, at most 20, as
`{tick, ms}`. The file adds `server.timeline` as two arrays of the same
length, `tick` and `ms`. While ticks are frozen the game time stays still, so
ticks repeat. Each time is labelled with the game time read at the end of the
tick it belongs to. Since the hook reads the previous tick's tally, that is
the game time the hook saw one tick earlier. The reply leaves the timeline out
(D5).

### D2. Counting block changes

A mixin at the head of `ServerChunkCache.blockChanged` counts while a capture
runs, on the server thread. The key is the block id from the level's state at
that position, plus the block entity type when one is there, plus where:
the sub-level whose plot holds the position, or none. The sub-level is looked
up once per chunk section and cached for the capture, since a plot's sections
belong to one sub-level. The summary's `server.block_changes` lists the top
20 keys by count, as `{block, be?, sub?, count, per_s}`.

### D3. Sampled stack walks for particle sources

At particle creation on the client, the first spawn of each type and every
32nd after it walk the stack with `StackWalker`. Frames of the JDK,
Minecraft, Mojang libraries, the loaders, Mixin and Blockpilot are skipped.
If Minecraft's client packet listener comes first, the source is `server`.
Otherwise it is the first remaining frame's class, or `minecraft` when no
frame remains. The same walk runs on the server thread at the head of the
`sendParticles` overloads that build the packet, keyed by the particle type,
with the source marked `server_side`. In 26.1 the shortest overload only
passes on to a longer one, so hooking it too would count twice. The
summary's `particles.sources` lists, per type spawned, its top three sources
by share: `{type, mod, class, via, share}`, where `via` is `client`,
`server` or `server_side`.

### D4. The mod a class comes from

`ClientPlatform` gains `modOf(Class)`. On NeoForge it is the mod file whose
module holds the class. On Fabric it is the mod whose origin paths hold the
class's code source. Results are cached per class. When neither knows the
class, the mod is left out and the class still names the source.

### D5. Reply and file

The JSON reply carries the full summary except `server.timeline`. The file
carries everything. The text overview adds `p99` to its MSPT and the top five
spikes, block changes and particle sources as further lists.

## Risks / Trade-offs

- [A stack walk costs microseconds] → only during a capture, on one spawn in
  32 per type.
- [`blockChanged` can run tens of thousands of times a second] → one block
  state read and a counter per call, during a capture only. The sub-level
  lookup is cached per section.
- [The class found may be a helper the spawner calls, such as a particle
  utility] → the mod is still right, and the class leads there.

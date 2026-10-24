# Design

## Context

Requested by the Aerolarge play-testing agent after the first jar. See
`proposal.md`. The engine, the game layer and the 1.21.1 mixin set from
`add-agent-control-mod` are in place. Sampling and the server clock come from
`add-test-harness`'s design (D7).

## Goals / Non-Goals

**Goals:**
- Observation through vanilla code paths that every mod's sounds, messages,
  particles and renderers pass through, so modded content is covered without
  integration.
- Zero cost when unused: ring buffers for logs, and a single volatile check per
  instrumented call for perf.

**Non-Goals:**
- GPU timing. Renderer times are CPU submission times.
- Audio content analysis. The log records which sound played, not what it sounds
  like.
- Packet byte counts for the integrated server's in-memory connection, which
  never serializes. Sizes are reported only for real network connections.

## Decisions

### D1. Logs are ring buffers stamped with the client tick

Sounds (2,000), messages (500) and per-tick particle counts (1,200 ticks) are
kept in fixed ring buffers on the client thread. Every entry carries the client
level's game time as `tick`. That is the same clock the server uses, so `tick
freeze`/`step` and `sample … clock=server` line up with it. Queries take
`since=<tick>` and return the current tick, so polling never misses or repeats
an entry.

### D2. Hook points (1.21.1)

- Sounds: `SoundEngine.play` right after `SoundInstance.resolve`, when volume
  and pitch are known.
- Messages: `Gui.setOverlayMessage`, `Gui.setTitle` and `Gui.setSubtitle`; chat
  through the existing chat hook.
- Particles: `ParticleEngine.add`.
- Camera: `Camera.setup` at TAIL. A placed camera overrides position, rotation
  and `detached`. Rendered angles are read from the camera after setup. Roll
  comes from NeoForge's `ViewportEvent.ComputeCameraAngles` at lowest priority,
  so mod changes are included. Fabric reports no roll.

### D3. Bursts and sampling are engine actions

`screenshot burst` is an action that captures from the framebuffer on its ticks.
`sample` is an engine meta command like `wait`: it parses its inner query, runs
it every N ticks on the matching clock, and collects rows with `tick`.

### D4. Performance capture

A `Perf` singleton has `volatile boolean active`. Mixins wrap:
- block-entity ticking (`LevelChunk.BoundTickingBlockEntity.tick`),
- entity ticking (`ServerLevel.tickNonPassenger`),
- block-entity rendering (`BlockEntityRenderDispatcher.render`),
- section compiles (`SectionRenderDispatcher.RenderSection` rebuild task),
- `Connection` send/receive.

Each wrapper does `if (Perf.active) { … nanoTime … }`. Server MSPT comes from
the server's own tick-time array, sampled each server tick; frame times come
from the client's frame timer. Accumulators are per-type maps of `{nanos,
calls}`, guarded per thread: server data on the server thread, client data on
the client thread, network data in concurrent maps. `perf stop` builds a Gson
tree, writes it to a file, and returns it as one `Result` field (`summary`) plus
a compact text overview.

## Risks / Trade-offs

- [Mixin targets in modded environments: other mods may also wrap these
  methods.] → Use `@Inject` HEAD/RETURN pairs rather than redirects, so wrapping
  composes.
- [Timing overhead distorts what it measures.] → `System.nanoTime` pairs cost
  about 25 ns. Mention the overhead in the summary as `instrumented_calls`.
- [The integrated server's local connection does not encode packets.] → Bytes
  are reported as 0 there and counted only on remote connections; packet counts
  are always exact.

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

### D5. Client options are a table over `OptionInstance`

The options `option` knows are a table of `options.txt` names and the
`Options` getter for each (`graphicsMode` → `Options::graphicsMode`, `maxFps` →
`framerateLimit`, `renderClouds` → `cloudStatus`, …), plus `hud`, which maps to
the plain `hideGui` field. Vanilla's own name-to-option walk
(`processOptions`) is private, and the table also keeps the list to options
that change what is rendered. Values are parsed from the current value's type:
booleans as `on|off`, enums by lower-case constant name (filtered through the
option's validator), numbers checked by the validator, whose range is found by
bisecting it, so out-of-range errors can name it. Setting goes through
`OptionInstance.set`, which runs vanilla's update hook (e.g. `allChanged` for
graphics mode) without the video screen's Fabulous warning. The first change
of each option records its original. Restoring sets the originals back the same
way. Nothing calls `Options.save`, so `options.txt` only changes if the player
saves the video screen.

After a change the action waits until `SectionRenderDispatcher.isQueueEmpty()`
has held for 3 consecutive client ticks (at least 2 ticks after the change, so
the renderer has scheduled the dirty sections), or answers `unsettled` with the
pending count at the timeout.

Restore points: the `leave` command; a world exit not started by Blockpilot
(detected as the level going away without `SessionCommands.disconnect`, which
`join`, snapshot restore and test-world creation use); the agent-control key
turning control off; and client shutdown. The placed camera already resets on
every world exit and now also resets on the key.

### D6. Contact sheets and crops are composed in Java

A crop is applied to the full framebuffer image before scaling. For a sheet the
burst keeps each scaled frame in memory instead of writing it, and composes them
when the last one arrives: `ceil(sqrt(n))` columns, tiles as wide as the
requested width or `1536 / columns`, whichever is smaller, and a label (`#<n>
t<tick>`) drawn in a built-in 3×5 pixel font scaled ×2 on a dark box in each
tile's corner. The game has no text rendering into images and AWT is
unreliable inside the game's process on macOS, so the font and the grid
layout live in a small class without game dependencies, and are unit-tested.

### D7. The pixel probe reads the last frame and its matrices

`pixels` is a query on the client thread. It reads the main render target with
`Screenshot.takeScreenshot`, as screenshots do, and accumulates per region the
channel sums and a 256-bin luma histogram (Rec. 601 weights) for mean, max and
p90. World positions are projected with the matrices of the frame just
rendered: a `LevelRenderer.renderLevel` HEAD inject records the projection
matrix (which includes view bobbing), the camera rotation matrix and the camera
position. A point maps to frame fractions through clip space; the radius maps
by projecting the point offset by `r` along the view's x and y axes. The
statistics and the projection are plain code with unit tests.

### D8. More client timings

- Particles: a `Particle` mixin adds a type tag, set in
  `ParticleEngine.createParticle` from the particle options' type; particles
  that mods add directly fall back to their class name. Ticks are timed with a
  HEAD/RETURN pair on `ParticleEngine.tickParticle`, renders by wrapping the
  `Particle.render` call in `ParticleEngine.render` with MixinExtras'
  `@WrapOperation` (shipped by both loaders), since only the call site knows
  which particle renders. Live counts are taken from the engine's particle
  queues when the capture stops.
- Entity renderers: a HEAD/RETURN pair on `EntityRenderDispatcher.render`, per
  entity type, with its own start stack.
- Render-level stages (NeoForge only): listeners for `RenderLevelStageEvent` at
  the highest and lowest priority record the time between them per stage, which
  is the time of every other handler of that stage. Per-mod attribution would
  need hooks inside the event bus. Fabric's world render events report nothing.

## Risks / Trade-offs

- [Mixin targets in modded environments: other mods may also wrap these
  methods.] → Use `@Inject` HEAD/RETURN pairs rather than redirects, so wrapping
  composes. Where only a call site knows the object (particle rendering),
  `@WrapOperation` chains with other mods' wrappers.
- [Timing overhead distorts what it measures.] → `System.nanoTime` pairs cost
  about 25 ns. Mention the overhead in the summary as `instrumented_calls`.
- [The integrated server's local connection does not encode packets.] → Bytes
  are reported as 0 there and counted only on remote connections; packet counts
  are always exact.
- [Particle timing calls `nanoTime` per particle, thousands per frame.] → Only
  during a capture; the summary's `instrumented_calls` shows the volume.
- [The pixel probe downloads the whole framebuffer on every run, a few ms at
  1080p.] → `wait pixels … every=<Nt>` spaces it out.
- [An agent's options persist if the player saves the video screen while they
  are changed.] → Restores happen at every point where agent control ends, and
  the README says to run `option reset` at the end of a test.

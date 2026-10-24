# Proposal

## Why

The first agent play-testing a mod with Blockpilot (Aerolarge: a Create/Sable
airliner with landing gear, air pockets, lightning and a laser) told us what it
could not verify. It can't hear, so it needs a log of every sound the client
plays. Effects last a few ticks, so a single screenshot misses them. Outside
views need the camera placed somewhere other than the player's eyes. Camera
shake and roll applied by mods are invisible in the player's own rotation.
Action-bar messages and particles carry feedback too. And a mod's performance
review needs measured numbers, comparable before and after a change. This change
adds those observation tools.

It builds on `add-agent-control-mod` and `add-test-harness` (sampling on the
server clock).

## What Changes

- **Sound log**: every sound the client starts (sound event id, category,
  position, volume, pitch, tick) in a ring buffer. `sounds [since=<tick>]
  [match=<pattern>] [limit=<n>]` reads it, and `sounds` results can be waited on
  (`wait sounds match=… until count>0`).
- **Message log**: chat, action-bar, title and subtitle messages with their kind
  and tick. `messages [since=<tick>] [kind=…]` reads them, and action-bar, title
  and subtitle also arrive as events.
- **Particle counts**: particles the client spawns, counted per type and tick.
  `particles [since=<tick>]` reports counts per type over a window.
- **Screenshot bursts**: `screenshot burst=<n> every=<Nt> [width]` captures n
  frames, one every N ticks, starting right away, and returns the files.
- **Camera placement**: `camera at <pos> [look <pos|e<id>>]` renders from a
  fixed world point, the player stays visible, and `camera reset` returns to the
  player's view.
- **Rendered camera angles**: `status` reports the camera's yaw, pitch and roll
  as rendered (including mod camera shake) when they differ from the player's.
- **Sampling**: `sample <query> every <Nt> x<n>` (as specified in
  `add-test-harness`) returns one row per run with the tick, for client queries
  too, e.g. per-tick player state.
- **Performance capture**: `perf start [name]` and `perf stop` produce a JSON
  summary, also saved to a file, covering:
  - server: MSPT avg/p95/max and TPS; tick time per block-entity type and per
    entity type; loaded block entity and entity counts.
  - client: FPS avg and 1% low; frame time; time in block-entity renderers per
    type; chunk section rebuilds per second; memory and GC.
  - network: packets and bytes per type, client-bound and server-bound.

  Instrumentation costs nothing while no capture runs.

## Capabilities

### New Capabilities
- `client-observation`: Sound log, message log, particle counts, screenshot
  bursts, camera placement and rendered camera angles.
- `performance-capture`: Start/stop performance captures and their JSON summary
  for server, client and network.

### Modified Capabilities
<!-- none: sampling is specified in add-test-harness and implemented with this
change -->

## Impact

- **Code**: new 1.21.1 mixins (sound engine, GUI overlay messages, particle
  engine, camera, block-entity ticking and rendering, entity ticking, section
  rebuilds, connection), plus `dev.blockpilot.game.observe` and
  `dev.blockpilot.game.perf`.
- **Output**: perf summaries under `<gameDir>/blockpilot/perf/`, burst frames
  under `<gameDir>/blockpilot/screenshots/`.
- **Overhead**: the logs are fixed-size ring buffers. Perf timing only runs
  between `perf start` and `perf stop`.

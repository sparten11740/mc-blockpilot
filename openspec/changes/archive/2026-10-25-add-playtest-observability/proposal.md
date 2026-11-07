# Proposal

## Why

The first agent play-testing a mod with Blockpilot (an aircraft mod with
landing gear, air pockets, lightning and a laser) told us what it could not
verify. It can't hear, so it needs a log of every sound the client
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

A second round of feedback from the same agent adds:

- **Client options**: `option <name> <value>` sets video options such as
  `graphicsMode` or `renderDistance` and waits for the chunks to rebuild;
  `option reset` puts back what agents changed. Checking how a mod looks on Fast
  graphics no longer means clicking through the video settings screen.
- **Agent changes end with agent control**: options and the placed camera are
  put back on `leave`, when the player leaves the world, on the agent-control
  key and on shutdown, so a test never leaves the player's game changed.
- **Contact sheets and crops**: `screenshot burst=<n> every=<Nt> sheet` saves
  the frames as one labelled grid, and `crop=` captures part of the frame, so
  motion is visible in one image.
- **Pixel probe**: `pixels` reports colour and brightness (mean, max, p90) of
  frame regions, or of the area around a world position, so a script can check
  that something rendered without reading the image.
- **More performance detail**: particles by type (live count, tick and render
  time), entity renderers by type, and on NeoForge the time per render-level
  stage.

## Capabilities

### New Capabilities
- `client-observation`: Sound log, message log, particle counts, screenshot
  bursts, contact sheets and crops, the pixel probe, camera placement, rendered
  camera angles, and client options with their restore.
- `performance-capture`: Start/stop performance captures and their JSON summary
  for server, client and network.

### Modified Capabilities
<!-- none: sampling is specified in add-test-harness and implemented with this
change -->

## Impact

- **Code**: new 1.21.1 mixins (sound engine, GUI overlay messages, particle
  engine, particles, camera, level rendering, block-entity ticking and
  rendering, entity ticking and rendering, section rebuilds, connection), plus
  `dev.blockpilot.game.observe` and `dev.blockpilot.game.perf`. On NeoForge two
  render-stage listeners time the stages.
- **Output**: perf summaries under `<gameDir>/blockpilot/perf/`, burst frames
  under `<gameDir>/blockpilot/screenshots/`.
- **Overhead**: the logs are fixed-size ring buffers. Perf timing only runs
  between `perf start` and `perf stop`.

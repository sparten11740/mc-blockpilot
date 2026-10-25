# Spec Delta

## Purpose

Measures a mod's cost on the integrated server, the client and the network
during a capture window, and summarizes it as JSON, so before/after runs can be
compared.

## ADDED Requirements

### Requirement: Capture window
`perf start [name]` SHALL start a capture and `perf stop` SHALL end it and
return its summary. Only one capture runs at a time. Starting a second fails
with `err busy`, and stopping without a capture fails with `err no_capture`.
`perf status` reports whether a capture runs and for how long.

#### Scenario: Before and after
- **WHEN** an agent runs `perf start before`, waits 30 s, runs `perf stop`,
  changes the mod, and repeats with `after`
- **THEN** two summaries with the same structure are returned and saved as
  `before.json` and `after.json`

### Requirement: Server metrics
When an integrated server runs, the summary SHALL include MSPT (average, p95,
max), TPS, the tick time per block-entity type and per entity type (total ms, ms
per tick, calls), and the counts of loaded block entities and entities per type
at the end.

#### Scenario: Ticking block entities ranked
- **WHEN** a capture runs while an airliner with dozens of ticking block
  entities flies
- **THEN** the summary lists block-entity types by total tick time, highest
  first

### Requirement: Client metrics
The summary SHALL include FPS (average and 1% low), frame time (average, p95,
max), CPU time spent in block-entity renderers per type and in entity renderers
per entity type, chunk section rebuilds (total and per second), heap use
(start, end, peak) and garbage collections (count and time).

#### Scenario: Expensive renderer found
- **WHEN** an autopilot screen renderer costs 3 ms per frame
- **THEN** its block-entity type appears at the top of the renderer list with
  about 3 ms per frame

### Requirement: Particle and render-stage metrics
For particles the summary SHALL include the live count per type when the
capture stops and the tick and render time per type. The type is the particle
type id when the particle was spawned from particle options, otherwise its
class name. On NeoForge it SHALL include the time per render-level stage,
summed over the handlers of that stage.

#### Scenario: Expensive particles found
- **WHEN** a mod's plasma particles take a large share of the render thread
- **THEN** their type tops the particle render list with its ms per frame, and
  the live counts show how many exist

#### Scenario: Render stage handlers
- **WHEN** a mod draws laser beams in the `after_particles` stage on NeoForge
- **THEN** `after_particles` appears in the stage list with the handlers' ms
  per frame

### Requirement: Network metrics
The summary SHALL include, per packet type and direction (client-bound,
server-bound), the packet count, packets per second and bytes, and for custom
payloads the payload id.

#### Scenario: Payload spam detected
- **WHEN** a laser sends one custom payload per shot at 20 shots per second
- **THEN** that payload id appears with about 20 packets per second

### Requirement: Results as JSON and file
`perf stop` SHALL return the summary as a structured result (a compact text
overview in text mode, the full tree in JSON mode) and save the full JSON to
`<gameDir>/blockpilot/perf/<name>.json`.

#### Scenario: Text overview
- **WHEN** a text-mode session stops a capture
- **THEN** the reply has one overview line (duration, MSPT, FPS, 1% low) and the
  top five entries of each list, plus the file path

### Requirement: No cost while idle
Instrumentation SHALL do no timing or counting work while no capture runs, apart
from a single check of whether a capture is active.

#### Scenario: Idle overhead
- **WHEN** no capture runs
- **THEN** block-entity ticking, rendering and packet handling take no
  Blockpilot timing calls

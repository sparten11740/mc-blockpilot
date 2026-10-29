# performance-capture Specification

## Purpose

Measures a mod's cost on the integrated server, the client and the network
during a capture window, and summarizes it as JSON, so before/after runs can be
compared.

## Requirements

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
p99, max), TPS, the tick time per block-entity type and per entity type (total
ms, ms per tick, calls), and the counts of loaded block entities and entities
per type at the end.

#### Scenario: Ticking block entities ranked
- **WHEN** a capture runs while an airliner with dozens of ticking block
  entities flies
- **THEN** the summary lists block-entity types by total tick time, highest
  first

### Requirement: Client metrics
The summary SHALL include FPS (average and 1% low), frame time (average, p95,
max), CPU time spent in block-entity renderers per type and in entity renderers
per entity type, chunk section rebuilds (total and per second), heap use
(start, end, peak) and garbage collections (count and time). From 26.1 on, a
renderer's CPU time covers both steps its code runs in, extracting its render
state and submitting it, and counts as one call.

#### Scenario: Expensive renderer found
- **WHEN** an autopilot screen renderer costs 3 ms per frame
- **THEN** its block-entity type appears at the top of the renderer list with
  about 3 ms per frame

#### Scenario: Renderer on 26.1
- **WHEN** on 26.1 a block-entity renderer spends 1 ms per frame extracting its
  render state and 2 ms submitting it
- **THEN** its type appears with about 3 ms per frame and one call per block
  entity and frame

### Requirement: Particle metrics
For particles the summary SHALL include the live count per type when the
capture stops and the tick and render time per type. The type is the particle
type id when the particle was spawned from particle options, otherwise its
class name. From 26.1 on, a particle's render time is the time its own code
takes to extract it into the frame, measured for quad particles, which are
nearly all of them.

#### Scenario: Expensive particles found
- **WHEN** a mod's plasma particles take a large share of the render thread
- **THEN** their type tops the particle render list with its ms per frame, and
  the live counts show how many exist

### Requirement: Particle sources
For each particle type spawned during the capture, the summary SHALL also name
its sources, from a sample of its spawns. A source is the mod and class of the
first code outside Minecraft, the mod loader and Blockpilot that spawned the
particle on the client, or `server` when the server sent it. In singleplayer,
particles the server sent SHALL also be attributed to the mod and class that
sent them on the server. Each source carries its share of the sampled spawns.

#### Scenario: Spawner named
- **WHEN** a burner block entity of a mod spawns `large_smoke` on the client
  every tick
- **THEN** the sources of `large_smoke` name that mod and the burner's class
  with most of the share

#### Scenario: Sent by the server
- **WHEN** a mod's server code sends `flame` particles to players in
  singleplayer
- **THEN** the sources of `flame` name that mod and class as sent by the
  server

### Requirement: Render-stage metrics
On NeoForge the summary SHALL include the time per render-level stage, summed
over the handlers of that stage, with the stages named as NeoForge names them
for that version.

#### Scenario: Render stage handlers
- **WHEN** a mod draws laser beams in the `after_particles` stage on NeoForge
- **THEN** `after_particles` appears in the stage list with the handlers' ms
  per frame

#### Scenario: Render stages on 26.1
- **WHEN** a mod draws in the stage after translucent particles on NeoForge
  26.1
- **THEN** `after_translucent_particles` appears in the stage list

### Requirement: Network metrics
The summary SHALL include, per packet type and direction (client-bound,
server-bound), the packet count, packets per second and bytes, and for custom
payloads the payload id. Packets that arrive in a bundle SHALL also be counted
by their own type.

#### Scenario: Payload spam detected
- **WHEN** a laser sends one custom payload per shot at 20 shots per second
- **THEN** that payload id appears with about 20 packets per second

#### Scenario: Spawns inside bundles
- **WHEN** a capture runs while entities spawn near the player
- **THEN** `add_entity` appears with one count per spawned entity, beside the
  bundles that carried them

### Requirement: Results as JSON and file
`perf stop` SHALL return the summary as a structured result (a compact text
overview in text mode, the full tree in JSON mode) and save the full JSON to
`<gameDir>/blockpilot/perf/<name>.json`. The per-tick timeline of server tick
times SHALL be only in the file.

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

### Requirement: Server tick spikes and timeline
When an integrated server runs, the summary SHALL list the server ticks that
took longer than 50 ms, at most 20 of them, longest first, each with its time
and the game tick it ended on. The game tick is the clock replies and
`sample` rows report. The saved file SHALL hold every server tick's time with
its game tick.

#### Scenario: A spike lined up with a sample
- **WHEN** a capture runs while `sample sable:info ship brief every 1t` traces
  a flight, and one tick takes 180 ms
- **THEN** the spikes list that tick with `ms` about 180 and its game tick,
  which is the `t<n>` of the sample row taken at that moment

### Requirement: Block changes by block
When an integrated server runs, the summary SHALL count the block changes the
server marks for sending to clients during the capture. It SHALL count them
per block id, with the block entity type when the block has one. The client
then also gets that block entity's data, if it sends any. With Sable it
SHALL count per sub-level the block is aboard, by its name. The list SHALL be
ranked by changes per second, highest first, with the count and the rate.

#### Scenario: A machine that updates too often
- **WHEN** a capture runs while a ship's thrusters change their block state
  every tick
- **THEN** the thruster's block id tops the list with its changes per second
  and the ship's name as its sub-level

#### Scenario: Settling terrain
- **WHEN** new chunks load and their leaves decay during the capture
- **THEN** the leaves appear with their own rate and no sub-level

### Requirement: What the server did on each tick
When an integrated server runs, a capture SHALL count for each server tick,
since the previous tick ended, the chunks generated, the chunks loaded from
disk, the chunks sent to clients and the entities added to the server's
levels. Spikes SHALL carry those counts that are above zero, the saved
timeline SHALL hold them for every tick, and the summary SHALL give their
totals.

#### Scenario: A stutter at chunk borders
- **WHEN** a capture runs while the player flies into terrain never visited,
  and the server slows each time a new row of chunks generates
- **THEN** those ticks are among the spikes with `chunks_generated` above
  zero

### Requirement: Block changes by position
When an integrated server runs, the summary SHALL also count per position the
block changes it counts per block, and list the 50 busiest positions as
`block_change_positions`, ranked by changes per second, highest first. Each
entry SHALL give the absolute position, in the plot for a block aboard a
sub-level, the block it last changed to, the sub-level's name, the dimension
when it isn't the overworld, the count and the rate.

#### Scenario: Which thrusters churn
- **WHEN** a capture runs while three of a ship's 33 thrusters change their
  block state every tick and the others stay still
- **THEN** `block_change_positions` lists those three plot positions first,
  each with the thruster's block id, the ship's name and about 20 changes per
  second

#### Scenario: Text overview
- **WHEN** an agent runs `perf stop` in text mode after such a capture
- **THEN** the reply has a `change at <x,y,z> <block> on <sub> <rate>/s` row
  for each of the five busiest positions

### Requirement: Bounded position tracking
A capture SHALL track at most 65,536 positions. Changes at positions beyond
that SHALL count only towards `block_change_positions_untracked`, which the
summary SHALL give when it is above zero. The counts by block SHALL still
include them.

#### Scenario: Large terrain update
- **WHEN** blocks at more than 65,536 positions change during one capture
- **THEN** `block_change_positions` ranks the positions tracked before the
  bound was reached, and `block_change_positions_untracked` counts the
  changes at the others

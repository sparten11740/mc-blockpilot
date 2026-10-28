## MODIFIED Requirements

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

### Requirement: Particle and render-stage metrics
For particles the summary SHALL include the live count per type when the
capture stops and the tick and render time per type. The type is the particle
type id when the particle was spawned from particle options, otherwise its
class name. From 26.1 on, a particle's render time is the time its own code
takes to extract it into the frame, measured for quad particles, which are
nearly all of them. On NeoForge it SHALL include the time per render-level
stage, summed over the handlers of that stage, with the stages named as
NeoForge names them for that version.

For each particle type spawned during the capture, the summary SHALL also name
its sources, from a sample of its spawns. A source is the mod and class of the
first code outside Minecraft, the mod loader and Blockpilot that spawned the
particle on the client, or `server` when the server sent it. In singleplayer,
particles the server sent SHALL also be attributed to the mod and class that
sent them on the server. Each source carries its share of the sampled spawns.

#### Scenario: Expensive particles found
- **WHEN** a mod's plasma particles take a large share of the render thread
- **THEN** their type tops the particle render list with its ms per frame, and
  the live counts show how many exist

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

#### Scenario: Render stage handlers
- **WHEN** a mod draws laser beams in the `after_particles` stage on NeoForge
- **THEN** `after_particles` appears in the stage list with the handlers' ms
  per frame

#### Scenario: Render stages on 26.1
- **WHEN** a mod draws in the stage after translucent particles on NeoForge
  26.1
- **THEN** `after_translucent_particles` appears in the stage list

### Requirement: Results as JSON and file
`perf stop` SHALL return the summary as a structured result (a compact text
overview in text mode, the full tree in JSON mode) and save the full JSON to
`<gameDir>/blockpilot/perf/<name>.json`. The per-tick timeline of server tick
times SHALL be only in the file.

#### Scenario: Text overview
- **WHEN** a text-mode session stops a capture
- **THEN** the reply has one overview line (duration, MSPT, FPS, 1% low) and the
  top five entries of each list, plus the file path

## ADDED Requirements

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

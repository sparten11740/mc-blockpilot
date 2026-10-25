# Spec Delta

## MODIFIED Requirements

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

### Requirement: Particle and render-stage metrics
For particles the summary SHALL include the live count per type when the
capture stops and the tick and render time per type. The type is the particle
type id when the particle was spawned from particle options, otherwise its
class name. From 26.1 on, a particle's render time is the time its own code
takes to extract it into the frame, measured for quad particles, which are
nearly all of them. On NeoForge it SHALL include the time per render-level
stage, summed over the handlers of that stage, with the stages named as
NeoForge names them for that version.

#### Scenario: Expensive particles found
- **WHEN** a mod's plasma particles take a large share of the render thread
- **THEN** their type tops the particle render list with its ms per frame, and
  the live counts show how many exist

#### Scenario: Render stage handlers
- **WHEN** a mod draws laser beams in the `after_particles` stage on NeoForge
- **THEN** `after_particles` appears in the stage list with the handlers' ms
  per frame

#### Scenario: Render stages on 26.1
- **WHEN** a mod draws in the stage after translucent particles on NeoForge
  26.1
- **THEN** `after_translucent_particles` appears in the stage list

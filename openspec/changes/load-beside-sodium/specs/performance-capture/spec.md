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

#### Scenario: Rebuilds beside Sodium
- **WHEN** a capture runs beside Sodium or Embeddium while chunk sections
  rebuild
- **THEN** the summary counts their rebuilds, as it counts the game's own

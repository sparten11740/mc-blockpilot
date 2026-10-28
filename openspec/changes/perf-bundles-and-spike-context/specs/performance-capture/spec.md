## MODIFIED Requirements

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

## ADDED Requirements

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

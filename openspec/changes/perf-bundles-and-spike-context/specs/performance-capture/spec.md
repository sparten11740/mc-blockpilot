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

### Requirement: Server tick spikes and timeline
When an integrated server runs, the summary SHALL list the server ticks that
took longer than 50 ms, at most 20 of them, longest first, each with its time
and the game tick it ended on. The game tick is the clock replies and
`sample` rows report. The saved file SHALL hold every server tick's time with
its game tick.

For each tick it SHALL also count, since the previous tick ended, the chunks
generated, the chunks loaded from disk, the chunks sent to clients and the
entities added to the server's levels. Spikes SHALL carry those counts that
are above zero, the saved timeline SHALL hold them for every tick, and the
summary SHALL give their totals.

#### Scenario: A spike lined up with a sample
- **WHEN** a capture runs while `sample sable:info ship brief every 1t` traces
  a flight, and one tick takes 180 ms
- **THEN** the spikes list that tick with `ms` about 180 and its game tick,
  which is the `t<n>` of the sample row taken at that moment

#### Scenario: A stutter at chunk borders
- **WHEN** a capture runs while the player flies into terrain never visited,
  and the server slows each time a new row of chunks generates
- **THEN** those ticks are among the spikes with `chunks_generated` above
  zero

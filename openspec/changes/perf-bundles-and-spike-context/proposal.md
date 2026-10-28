# Proposal

## Why

Two gaps showed up when a capture was used to find a stutter:

- The network list counts a bundle as one packet. Minecraft sends an entity's
  spawn and its first data as a bundle, so `add_entity` read 0 while planes
  were spawning.
- Spikes say when the server was slow but not what it did. A zeppelin
  stuttered at every chunk border: those ticks took 31 to 107 ms against 5 to
  8 ms otherwise, because new terrain was generating. Finding that took
  matching the spikes' ticks against the player's position by hand.

## What Changes

- Packets that arrive in a bundle are counted by their own type too, so a
  spawn bundle counts its `add_entity`.
- Each server tick of a capture records how many chunks were generated,
  loaded from disk and sent to clients, and how many entities were added,
  since the previous tick ended. Spikes carry these counts when any are
  above zero, and the saved file's timeline holds them for every tick. The
  summary gives the totals.

## Capabilities

### New Capabilities

None.

### Modified Capabilities
- `performance-capture`: bundle contents in the network list, and what the
  server did on each tick of the timeline and each spike.

## Impact

- `game/perf/Perf.java`, `PerfCommands.java` (summary, text overview).
- Mixins for both versions: the connection (existing, bundles), the server's
  chunk sending, and the server's entity manager. The loaders' chunk load
  events, through the platform hooks.
- README: the performance row.

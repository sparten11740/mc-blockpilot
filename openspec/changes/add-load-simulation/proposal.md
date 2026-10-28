# Proposal

## Why

A mod can behave well in a quiet test world and badly on a busy server, on a
slow machine or over a laggy connection: a moving entity jitters, a machine
desyncs, a screen lags behind. Reproducing that needs the integrated server,
the client or the connection slowed on purpose, one at a time. `tick rate`
slows the client's ticks too, so it can't show a slow server alone. The
workarounds agents build instead change the test world: thousands of mobs in
a forceloaded chunk can hang the save for minutes, and command blocks that
burn time can flood chat until the client stops. Nothing in Blockpilot adds
load without touching the world.

## What Changes

- `load server <ms> [jitter=<ms>]` makes every integrated-server tick take
  that much longer, idle on the server thread inside the tick. The server's
  own tick time (MSPT, and `perf`) shows it, and TPS falls once a tick takes
  over 50 ms.
- `load client <ms> [jitter=<ms>]` makes every frame take that much longer,
  idle on the render thread.
- `load net <ms> [jitter=<ms>]` holds every packet on the client's connection
  that long in each direction, keeping their order, to mimic LAN or online
  play against the integrated server.
- `load` reports what is set, `load off` ends all of it, and `0` ends one.
  Load also ends when the player leaves the world, when agent control is
  turned off and when the game closes. Nothing is written to the world or the
  options.

## Capabilities

### New Capabilities
- `load-simulation`: adding server, client and network load on purpose,
  without changing the test world.

### Modified Capabilities

None.

## Impact

- A new `game/load` package for the settings and the delaying network
  handler, and a `load` command in the performance group.
- Mixins for both Minecraft versions: the server's tick, the client's frame
  (the existing frame mixin) and an accessor for the connection's channel.
- `game/BlockpilotClient.java`: ending load on leave, on agent control off and
  on close.
- README: the performance row.

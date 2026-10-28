# load-simulation Specification

## Purpose

Adds load to the integrated server, the client or the connection on purpose,
without changing the test world, so a mod's behaviour on a busy server, a
slow machine or a laggy network can be reproduced and measured.

## Requirements

### Requirement: Server load
`load server <ms> [jitter=<ms>]` SHALL make every tick of the integrated
server take that many milliseconds longer, each tick a uniform random amount
within the jitter around it, spent idle on the server thread inside the tick.
The server's own tick time SHALL include it, so `perf` and the debug screen
show it, and the server falls below 20 TPS once a tick takes longer than
50 ms. Nothing in the world SHALL change. Without an integrated server it
SHALL fail with `err no_server`.

#### Scenario: A busy server
- **WHEN** an agent runs `load server 60; perf start; wait 200t; perf stop`
- **THEN** the summary's MSPT is about 60 ms and its TPS about 16

#### Scenario: Uneven ticks
- **WHEN** an agent runs `load server 30 jitter=20`
- **THEN** server ticks take from about 10 to about 50 ms longer than before

### Requirement: Client load
`load client <ms> [jitter=<ms>]` SHALL make every frame take that many
milliseconds longer, within the jitter as for the server, spent idle on the
render thread.

#### Scenario: A slow machine
- **WHEN** an agent runs `load client 50` where the client ran at 120 FPS
- **THEN** it runs at under 20 FPS

### Requirement: Network delay
`load net <ms> [jitter=<ms>]` SHALL hold every packet on the client's
connection that many milliseconds in each direction, within the jitter as for
the server, and SHALL deliver the packets of each direction in the order they
were sent.

#### Scenario: A server across the internet
- **WHEN** an agent runs `load net 100` and then has the integrated server
  place a block
- **THEN** the client sees the block about 100 ms after the server placed it

### Requirement: Load settings
`load` SHALL report the server, client and network load set, leaving out what
is off. `load off` SHALL end all of it, and a load of `0` ends that one. The
server and client loads SHALL take 0 to 1,000 ms and the network delay 0 to
2,000 ms, each with a jitter of at most its load. Load SHALL also end when
the player leaves the world, when agent control is turned off and when the
game closes, and SHALL never be saved with the world or the options.

#### Scenario: Report and end
- **WHEN** an agent runs `load server 40 jitter=10; load net 80; load`
- **THEN** the last reply is like `ok server=40±10ms net=80ms`, and after
  `load off` it is `ok`

#### Scenario: Left on leave
- **WHEN** an agent runs `load server 200`, leaves the world and joins it
  again
- **THEN** the server runs at full speed

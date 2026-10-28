# Design

## Context

- The integrated server tallies a tick's time from the start of
  `MinecraftServer.tickServer` until just before the end-of-tick event, so
  work in Blockpilot's end-of-tick hook doesn't count. `tickChildren`, which
  ticks the levels and connections, runs inside that window. It has that name
  in 1.21.1 and 26.1.
- The existing frame mixin runs at the head of `Minecraft.runTick`, once per
  frame.
- An in-memory connection carries packet objects through a Netty pipeline
  whose last handler is the connection itself (`packet_handler`). Packets the
  client receives pass through the pipeline before it. Packets it sends enter
  at the tail and pass every handler before it. A remote connection has the
  same last handler.
- `tick rate` and `tick freeze` are undone when the server stops. Options and
  configs an agent changed are put back when the player leaves the world, when
  agent control is turned off and when the game closes.

## Goals / Non-Goals

**Goals:**
- Slow the server, the client or the connection, one at a time or together,
  with nothing added to the world.

**Non-Goals:**
- Packet loss, bandwidth limits or reordering. The connection Minecraft uses
  delivers in order, so only delay and jitter fit.
- Load on a remote server. Only the client's connection can be delayed there.

## Decisions

### D1. One `load` action

`load` is an action in the performance group that finishes at once, so it
runs in order with the actions around it. Its forms are `load`,
`load server <ms>`, `load client <ms>`, `load net <ms>` (each with
`jitter=<ms>`), and `load off`. Every reply lists what is set, as
`server=40±10ms client=5ms net=80ms`, leaving out what is off, with the
values also as fields (`server_ms`, `server_jitter`, …). Out of range values
are `err syntax`, naming the range. The server form needs the integrated
server (`err no_server`); the net form delays whatever connection the client
has. Neither needs cheats, since nothing in the world changes.

### D2. Server load inside the tick

A mixin at the head of `MinecraftServer.tickChildren` parks the server thread
for the load (load ± a uniform random part of the jitter, at least 0) when one
is set. That is inside the tallied window, so MSPT, the debug screen and
`perf` all show it. Parking keeps the CPU free, which matters when the client
shares the machine. Load is a pair of volatile fields, so the hook costs one
read when no load is set.

### D3. Client load at the start of a frame

The existing frame mixin parks the render thread the same way. Client ticks
run inside frames, so they still catch up to 20 per second until frames get
longer than a tick.

### D4. A delaying handler before the packet handler

`load net` adds a duplex handler named `blockpilot_delay` before
`packet_handler` in the client connection's pipeline, reaching the channel
through an accessor mixin. For each direction it computes a release time,
now + delay ± jitter, but never before the previous packet's release. It then
schedules the packet on the channel's event loop, so the order holds and
jitter only bunches packets. Writes are flushed as they are released. Flushes
asked for in between are dropped, since every release flushes. Once added,
the handler stays until the connection closes and passes packets straight on
while the delay is 0 and nothing is held. That way ending the delay never
reorders the packets still held.

### D5. When load ends

`load off`, a load of 0, turning off agent control, leaving the world and the
game closing end the load, through the hook that puts back options and
configs. The server's load also ends when the server stops. Load lives only
in memory, so nothing saves it.

### D6. Limits

Server and client loads take 0 to 1,000 ms, a tick or frame of a second at
most. The network delay takes 0 to 2,000 ms, well inside the 15 s keep-alive
limit even with a long round trip. The jitter is at most the load, so the
time added is never negative.

## Risks / Trade-offs

- [A long server load can make the client wait on the server, as in
  `side=server` reads] → each wait stays within the usual 5 s limit. The
  load is at most a second per tick.
- [Delaying packets during a protocol switch could reorder it with the
  pipeline changes Minecraft makes then] → the handler sits before the packet
  handler, which those switches don't move. A delay is meant for play, where
  switches are rare.

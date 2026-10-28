# Design

## Context

- Where a sample or a wait runs is decided by
  `CommandSpec.runsOnServer()`: `Parser.checkClock`, `Statement.Sample.lead`,
  `Engine.sample` (the sampler's clock), the sampler's grouping of
  server-side queries into one trip (`GameBridge.callOnServer`), and
  `Engine.waitUntil`. A query with a `side` option, such as `block`, `find`,
  `sable:info` or `entity`, runs on the client and reads the server itself
  through `Sides.onServer`. That submits the read to the server and waits up
  to 5 s.
- On the server thread, a task submitted from inside another task the server
  is running waits in its queue. Waiting for it there blocks until the 5 s
  limit. The sampler's trip runs its queries inside such a task.
- Client-clock rows carry `tick` from `GameBridge.gameTime()`, the integrated
  server's game time read from the client thread.
- `Load.server()` holds the server load an agent set. The engine can't see it.

## Goals / Non-Goals

**Goals:**
- Server readouts of one run read one moment of the server, and rows of a
  client-clock sample can be ordered and paired.
- Measurements that the sampler itself disturbs are marked.

**Non-Goals:**
- Changing where a lone `side=server` query runs. It keeps its own trip.
- Reading the server without holding the client. A trip that doesn't wait
  would hand its rows in a tick or more later.

## Decisions

### D1. `Statement.Call.runsOnServer()`

A call runs on the server when its command does, or when it is given
`side=server`. Every place above that decides a clock or the trip asks the
call instead of its command. A lone query keeps its placement, since
`ScriptJob` places single calls by their command.

### D2. Server reads run at once on the server thread

`Sides.onServer` and `Sides.tryServer` run the read directly when they are
already on the server thread, with the same error handling, instead of
submitting it and waiting. So a `side=server` query works on the server clock
and inside the sampler's trip.

### D3. `client_tick`

`GameBridge.clientTicks()` is the number of client ticks since the game
started, a counter Blockpilot already keeps. Every row a client-clock sample
takes gets `client_tick`, printed `c<n>` right after the tick, before the
command name. All rows of one run share it.

### D4. `held_client=<runs>`

`GameBridge.serverLoad()` gives the server load in ms, or 0. A client-clock
sample counts the runs that made the server trip while it was above 0, and
when there were any its result adds `held_client=<runs>` with the note
`(reads of the server under load server held the client)`. The help of
`sample` and `load` says that a read of the server from the client waits for
the server's tick.

## Risks / Trade-offs

- [Rows gain a field] → one short token per row, on the client clock only.
- [A `side=server` read on the server clock reads client state for things
  such as `here` or the player's position] → those are plain field reads
  from another thread, as `sable:list side=server` already does.

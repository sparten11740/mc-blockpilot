# Design

## Context

- `entity` reads one entity by id through `Sides.read`, on the client or the
  integrated server. The `e<id>` argument is an `ArgType.ENTITY` slot. A slot
  can also take keywords.
- The client applies entity updates in `ClientPacketListener`, one handler
  per packet type, after `PacketUtils.ensureRunningOnSameThread` has moved
  the call onto the client thread. The handlers and their packets differ
  between versions. Both have `handleMoveEntity`, `handleTeleportEntity`,
  `handleSetEntityMotion` and `handleSetEntityData`. 26.1 adds
  `handleEntityPositionSync`, which carries the regular position updates
  there.
- `Entity.tickCount` counts the ticks an entity has existed in this copy.

## Goals / Non-Goals

**Goals:**
- The player and any entity sampled on both sides, with how its updates
  arrive.

**Non-Goals:**
- Counting updates on the server, which sends them. The client's side is
  where jitter shows.

## Decisions

### D1. `me`

The `ref` slot takes the keyword `me` beside `e<id>`. On the client it is the
local player. With `side=server` it is the server's player with the local
player's UUID. Replies add `ref` (`e<id>`) after the type, so `entity me`
tells the player's id.

### D2. `age`

`age=<ticks>` from `tickCount`, on both sides. A copy the client created
again starts from 0, while the server's copy keeps counting.

### D3. Updates per tick

A small counter keyed by entity id records, per kind, the updates the client
applied. Mixins on the four handlers count after the thread check, in both
versions, plus `handleEntityPositionSync` in 26.1, which counts as `move`.
Teleports count as `teleport`, motion packets as `motion`, entity data as
`data`. The counter clears at the end of every client tick, after the
engine's steps, so a client-clock sample reads the updates since the
previous tick. The `entity` reply adds `net` with the non-zero kinds, printed
`net move1 data1`, on the client only.

## Risks / Trade-offs

- [Counting costs a map update per entity packet, also when nobody asks] →
  a few hundred per tick at most, cleared each tick.
- [26.1 also sends a teleport packet for some moves] → it counts as
  `teleport`, which is what the client applied.

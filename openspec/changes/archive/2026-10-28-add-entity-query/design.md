# Design

## Context

- An entity reference `e<id>` is the entity's network id. In singleplayer the
  client and the integrated server use the same id for an entity.
- `Sides.read` runs a read against the client's level, or with `side=server`
  against the integrated server's level on its thread, after checking for the
  server and cheats. `block`, `blocks`, `find` and `sable:find` use it.
- `status` prints the player's position with hundredths, and its velocity in
  blocks per tick with thousandths, leaving the velocity out while the player
  rests and printing `airborne` when not on the ground. `look` rows give an
  entity's position relative to the player, in tenths.
- A query that does its own server read, like `sable:info side=server`, runs
  on the client, so a sample of its client and server forms runs on the
  client's ticks.

## Goals / Non-Goals

**Goals:**
- One entity's motion, on either side, in a row short enough to sample every
  tick.

**Non-Goals:**
- The client's interpolation target (where the server last placed the
  entity). Minecraft keeps it differently in 1.21.1 and 26.1, and the server
  row gives the position the target comes from.
- Health, equipment or NBT. `look`, `nbt` and `status` cover those.

## Decisions

### D1. Fields

`type` (bare), `pos` (absolute, thousandths), `yaw` and `pitch` (tagged,
tenths), `vel` (blocks per tick, thousandths), and `airborne` when not on the
ground, as `status` prints them. Positions use thousandths, not the
hundredths of `status`, because the drift between client and server is often
below a centimetre. Positions are absolute, so rows taken while the player
moves compare directly. A zero velocity is left out, as the language leaves
out default values. With `side=server` the row adds `uuid`, as server-side
entity rows do elsewhere.

Text replies print numbers to the hundredth, so the query prints `pos` and
`vel` itself, to the thousandth. JSON rounded every number to the hundredth
too. It now writes a position's coordinates to the thousandth, so the JSON
form keeps them as well. Other commands round their positions to hundredths
before replying, so they read as before. The exception is `status`'s velocity,
which it always rounded to thousandths and which JSON now keeps.

### D2. Reading through `Sides.read`

The handler reads the entity by id through `Sides.read`, so the server form
gets the usual `no_server`, `no_cheats` and five-second limit, and never
loads chunks. An id the side doesn't hold is `err no_entity <ref>`, the code
`nbt` and actions use.

## Risks / Trade-offs

- [The server form reads the server in a hop of its own, so in a sample its
  row can be from the next server tick] → as for `sable:info side=server`.
  Rows carry the tick of the client's run.

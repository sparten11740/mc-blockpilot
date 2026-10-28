# Design

## Context

- `sable:tp` picks the spot from the server's copy of the plot, teleports
  the server's player to the spot's world position under the server's pose,
  then waits two ticks and answers from the server step.
- Sable's client poses each sub-level from server snapshots, stepped back by
  its interpolation delay (client config
  `sub_level_snapshot_interpolation_delay_ticks`, 1.5 by default). The
  client therefore sees a moving sub-level about a tick and a half behind
  the server.
- While Sable tracks the local player on a sub-level, the client sends the
  player's position in plot coordinates, and the server places its player
  there under the server's pose. Where the client puts the player in the
  plot is what holds.
- The client starts tracking a sub-level when a move of the player collides
  with it from above, and keeps tracking while any block of it is within a
  block of the player's box.
- Blockpilot reaches Sable only through the companion API. It can map plot
  points to world points and read velocity on either side, and it can read
  the sub-level an entity stands on or rides. It cannot set what the
  player is tracking.
- `use` at a plot position already aims through the client's copy each tick,
  so the obstruction it reported came from where the player stood.

## Goals / Non-Goals

**Goals:**
- After `sable:tp`, the player stands on the chosen spot in the plot as the
  client and server both have it, at any speed, or the command fails.
- Agents hear when the player gets on or off a sub-level.

**Non-Goals:**
- Changing how `use` aims, or how the spot is chosen.
- Boarding without the integrated server; `sable:tp` already needs it.

## Decisions

### D1. Place the player through the client's copy

The server step stays as it is. It finds the spot in the server's copy,
which also covers a sub-level the client hasn't received, and moves the
server's player there so the client gets the chunks and the sub-level.
Then, on each client tick until done, while the client holds the sub-level
and the player isn't yet on the spot:

- while the client doesn't track the sub-level yet: early in each client
  tick Sable moves its sub-levels, and only then does the player move. So
  put the player where the spot's floor centre will be after that step (the
  spot through the client's copy plus the sub-level's velocity there for
  one tick), a quarter block above it, with a downward motion of half a
  block. Their next move then lands on the deck, which starts the tracking.
  The quarter block covers a step that comes out a little longer or shorter
  than the velocity says. Moving the player along with the deck instead
  (motion equal to the deck's velocity) never touches the deck, so it never
  tracks: tried on the dev client;
- once tracked, put the player on the spot through the client's copy with
  no motion, since Sable carries a tracked player from one pose to the
  next;
- stop creative flight, since a flying player never touches the deck.

Placing again on every tick until done also undoes the server's own
teleport, should its packet reach the client after the first placement.

Alternatives:
- Leading the server teleport by the client's lag (velocity times the
  interpolation delay). The delay is a client setting and the effective lag
  varies with tick timing, so the lead would only be close.
- Setting Sable's tracking directly. The companion API has no call for it,
  and Sable's internals are off limits to the integration.

### D2. When it is done, and when it gives up

It is done once the client shows the player carried by that sub-level with
the feet within 0.6 blocks of the spot's floor centre in the plot. The 0.6
allows a bottom-slab floor, which drops the feet half a block into the cell
below. It is also never done before the two settle ticks have passed. The
reply keeps the server step's fields, except that `at` becomes the player's
world position at that moment.

After 100 ticks (five seconds) it fails with `err not_aboard`. That is long
enough for a client that first has to receive the sub-level after a long
teleport. The detail says what the client shows: no copy of the sub-level,
or where the player is in its plot and which sub-level, if any, carries
them.

### D3. Boarding and leaving events

A small watcher in `game/sable/` remembers the sub-level carrying the player
(its UUID and name). Once per client tick, before the engine's steps, it
compares that with the one carrying the player now. When it changes, it
emits `left` for the old one, then `boarded` for the new one, through the
extension API's event hook. The text form is the sub-level's name, as in
`status`, and JSON also carries `id`. A sub-level that is gone keeps the
last name seen. Without a player or a level it forgets the old sub-level
without emitting, so joining and leaving worlds stay quiet. A failed call
into Sable skips that tick and changes nothing.

Running before the engine's steps puts `boarded` on the reply of the
`sable:tp` that caused it.

## Risks / Trade-offs

- [The client moves the player, not the server] → in singleplayer the
  server takes the owner's moves without the speed check, and Sable doesn't
  check the moves of a player it tracks. `sable:tp` needs the integrated
  server and cheats anyway.
- [A later Sable could start tracking differently] → `sable:tp` then fails
  with `err not_aboard` rather than leaving the player off the spot without
  saying so.
- [Leaving events while jumping or flying] → jumping on deck keeps the
  tracking, since the deck stays within a block of the player's box. Flying
  off counts as leaving, as it should.
- [26.1 has no Sable integration] → the code builds for every target and
  runs only where the integration is on.

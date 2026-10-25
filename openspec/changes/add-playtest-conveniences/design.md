# Design

## Context

See proposal.md for why. The current state these decisions build on:
- `leave` saves singleplayer through the session commands and waits for the
  title screen; nothing closes the game. The CLI already checks whether the
  game's process is alive, to spot a stale `endpoint.json`.
- `face` and `use` turn a position argument into an aim point: the centre of
  the block's shape for whole coordinates, else the exact point. A plot point
  follows its sub-level, and `use` aboard presses only when the crosshair rests
  on the target.
- `status` prints the crosshair's block, position and face. The exact hit
  location is only in JSON (`aim_at`), in world or plot coordinates.
- The `screenshot` action reads the frame rendered before its first tick, and
  so does the MCP tool. Its requirement says "the next rendered frame".
- `messages` filters by tick and kind only.

## Goals / Non-Goals

**Goals:**
- One block-frame convention, shared by aiming (`at <u,v,w>`) and reporting
  (`hit=`), so a point read off the crosshair can be passed back unchanged.
- `quit` closes the game the same way its Quit Game button does, never by
  killing the JVM.

**Non-Goals:**
- Block frames for blocks facing up or down, or turned by other properties
  (`rotation`, `axis`): those use the unturned frame.
- Closing a remote server: `quit` closes this client only.
- A delay option on `screenshot`: `wait <Nt>; screenshot` covers longer
  effects.

## Decisions

### D1. `quit` leaves, answers, then stops the game

- **Order:** if a world is loaded, `quit` leaves it the way `leave` does and
  waits for the title screen. It answers `ok pid=<pid>` and, a few ticks
  later, calls the game's own stop. The game then shuts down normally: mods'
  stopping hooks run, and Blockpilot's removes `endpoint.json`.
- **Why the delay:** the reply goes out on the endpoint's threads, so it is
  flushed before the game starts shutting down.
- **Gating:** `quit` is an action, so F8 (agent control off) blocks it like any
  other action.
- **Alternatives:**
  - `System.exit` skips saving and the mods' shutdown hooks.
  - Answering after the stop is impossible, because the endpoint closes with
    the game.

### D2. The CLI waits for the pid in the reply

- **What:** after printing the reply, if a result is `ok pid=<pid>` (JSON: a
  `pid` field on a `quit` result), the CLI polls that process with the
  existing liveness check until it is gone or `--timeout` runs out.
- **Exit codes:** 0 once the process is gone; 2, with the pid on stderr, if it
  is still running at the timeout.
- **Why the reply's pid:** the readiness file is removed as the game closes,
  and the reply names exactly the process that is quitting.

### D3. The block frame is the model's north-facing frame

- **Coordinates:** (u, v, w) are the coordinates a block model is drawn in when
  it faces north: x west to east, y up, z north to south, each 0 to 1.
- **The turn:** a horizontal `facing` (or `horizontal_facing`) property turns
  the frame about the block's vertical centre line in quarter turns, clockwise
  seen from above: north 0, east 1, south 2, west 3, as blockstate files turn
  models. One quarter turn maps (x, z) to (1 - z, x).
- **The helper:** a game-free class converts both ways: block frame to block
  offset for aiming, block offset to block frame for `hit=`. Its unit tests
  check all four facings and the round trip.
- **Aiming:** `face/use <pos> at <u,v,w>` turns the point into an offset inside
  `pos`'s block and aims at `pos + offset` as an exact point. For a plot
  position everything else is unchanged: it follows the sub-level, and `use`
  presses only when the crosshair rests on that block.
- **The argument:** `<u,v,w>` is written as a position token without `~`, so the
  grammar needs no new token type.
- **Alternatives:**
  - A frame relative to the clicked face is ambiguous for points inside a
    block and on corners.
  - World axes force agents to redo the turn for every facing, which is the
    hand computation this removes.

### D4. `hit=` comes from the crosshair's hit location

- **What:** `status` takes the hit location (plot coordinates aboard a
  sub-level) minus the block position, converts it to the block frame with the
  block's facing, clamps it to 0 to 1 against rounding, and prints two
  decimals.
- **JSON:** the field is `hit` with three numbers. `aim_at` stays as it is.

### D5. A text pattern for the message log

- **What:** `match=` is a glob over the whole message text, ignoring case: `*`
  matches any run of characters, everything else matches itself.
- **The helper:** compiled once per query to a regular expression, with
  literal parts quoted. It is a small game-free helper with unit tests.
- **Spaces:** an option value can't hold a blank, so `*` stands in for spaces
  as well: `match=*gear*refused*`.

### D6. Screenshots wait for a frame rendered after they start

- **What:** a frame counter goes up at each frame boundary (the existing frame
  hook). The `screenshot` action notes the counter when it starts. It captures
  on the first tick where the counter has moved on, i.e. from the frame
  rendered after the request. The MCP tool's request comes from a network
  thread, possibly while a frame is rendering, so it waits for two frame
  boundaries instead of one.
- **Bursts:** later frames keep their `every=` spacing. With `hud=off` the
  existing one-tick wait already satisfies the rule.
- **Cost:** at most one frame of latency.
- **Alternative:** an `after=` option, which would leave the default
  contradicting the requirement.

### D7. Height above ground and the brief readout

- **Height above ground:** the client level is scanned straight down from the
  bottom of the sub-level's world bounds, under the centre of those bounds,
  to the first block with a collision shape. `agl` is the distance from the
  bounds' bottom to the top of that shape, to one decimal.
  - Sub-level blocks live in their plots, so the column holds only world
    blocks, and the ship can't measure itself.
  - An unloaded chunk or the bottom of the world gives `none`.
- **Brief:** `sable:info <sub> brief` prints `yaw`, `pitch`, `roll`, `speed`,
  `climb` (vertical velocity) and `agl` only. The full reply gains `agl`.

## Risks / Trade-offs

- [A mod draws its models facing another way than north] → `hit=` uses the
  same frame, so a point read off the crosshair always comes back right; only
  coordinates taken from the model file need the mod's convention.
- [`quit` from an agent ends a human's session] → It is an action, so F8
  blocks it, as it blocks `leave`.
- [The column scan for `agl` runs on every sample] → It is one column of at
  most the world height, from loaded chunks only.
- [Waiting for the next frame slows a screenshot when the game renders slowly]
  → The wait is one frame at most, however long that frame takes.

## Migration Plan

Nothing to migrate. This change adds requirements to `dev-integration` and
`sable-sublevels`, which `add-test-harness` and `add-sable-support` create, so
it is archived after those two.

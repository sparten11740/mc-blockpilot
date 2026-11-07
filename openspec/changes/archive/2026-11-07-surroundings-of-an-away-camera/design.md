# Design

## Context

- `AwayCamera.presence(ServerPlayer)` is where the server takes a player to
  be: its away camera's place in its own dimension, else where it stands.
  The server-side mixins load, tick and send around it.
- On the client, `AwayCamera.centre()` is the away camera's place or null,
  and `NamedEntities` measures from it or else from the player.
- `look` and `find` centre their entity box and block scan on the player's
  block, and `find` sorts entities by distance from the player.

## Goals / Non-Goals

**Goals:**
- `look` and `find` that see what a camera placed away sees.

**Non-Goals:**
- Changing what `~` means: actions move the player, so a relative position
  stays the player's.
- A centre an agent picks freely, such as `find … around <pos>`; `block`
  and `blocks` already read any loaded position.

## Decisions

### D1: One point the client measures from

`AwayCamera.clientPresence()` gives the away camera's place while one is
placed, else the player's position: the client's counterpart of
`presence`. `look`, `find` and `NamedEntities` all take it, so the three
agree, and a camera placed without `away` changes nothing, as the world
around it isn't loaded by its being there.

### D2: Only the centre moves

`look` and `find` scan around the block of that point and sort by distance from
it; the player stays excluded from what they list. `look` today passes the
player as the entity to skip and searches its own bounding box; it takes an
explicit box round the point instead and still skips the player. Rows keep `at`
and `near` relative to the player's block, as every reply does, and the hidden
absolute positions beside them.

## Risks / Trade-offs

- [A script that runs `look` or `find` with an away camera expects the
  player's surroundings] → Beyond the view distance none arrive, so it got
  nothing; within it, it now gets the camera's surroundings. Accepted: the
  camera is where the server takes the player to be, and `camera reset`
  brings the player's surroundings back.
- [Relative positions far from the player are long] → The absolute ones are
  beside them, in JSON as `at_abs` and `near_abs`.

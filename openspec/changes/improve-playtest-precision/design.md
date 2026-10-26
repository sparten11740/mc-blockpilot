# Design

## Context

`server` builds its default source at the overworld's spawn; `as=player`
uses the player's own source. `use` checks its target only aboard a
sub-level: there `AimedClick` turns the player towards the block just before
the game picks the crosshair's hit, and presses only on a tick whose hit is
that block, failing after half a second with `obstructed` or `out_of_reach`.
Everywhere else `use` aims once and clicks whatever the hit is. `sable:tp` is
a server-thread action that teleports onto the nearest free spot within two
blocks. `blocks` builds its palette and symbols in `Regions`, and `grid` has
its own writer. See proposal.md for why.

## Goals / Non-Goals

**Goals:**
- One click path for every checked target, so a block in the world, an
  entity and a block aboard behave alike.
- Each new reply field costs nothing when there's nothing to say: it is left
  out, as Blockpilot leaves out every default.

**Non-Goals:**
- Clicking a hidden block through what covers it. A player can't, and the
  server would see a click no player could make.
- Variables in scripts. Boarding by block covers the case that needed them.

## Decisions

### D1. The default source is the player's place, not the player

Without `as=player`, `server` builds the server's own source from the server
player's position, rotation and level, with permission 4 and no entity, so
`@s` still matches nothing and a mod command that needs a player still says
so. Without a server player (not yet joined) it keeps the spawn source.

*Alternative:* run as the player by default. That changes `@s` and which
commands accept the source, a larger change than where `~` counts from.

### D2. `use` presses through `AimedClick` for every checked target

`AimedClick` takes a test of the hit instead of a block position: the block
at a world or plot position, or the entity. `use` arms it for a position
whose block has an outline shape and for an entity, and keeps the half-second
limit and the `obstructed`/`out_of_reach` errors of the path aboard. A target
block with no shape, and `use` without a target, press as before. `AimedClick`
turns the player itself, so an armed `use` drops the separate aiming tick
(keeping one only to let a held sneak key register), and a clear target
answers on the same tick as today.

### D3. `any` compares the hit after the click

`use … any` presses as before. With the hit it reads on the tick the click is
handled, it runs the same test as D2 and adds `off_target=<target>` when the
test fails: the position as given, or `e<id>`.

### D4. `sneak_item` is read before the press

The main hand's item, else the off hand's, is read just before the press,
since using it may use it up (placing the last block). The field is added
only with `sneak` and an item in hand.

### D5. `sable:tp` checks sight in the plot

The plot holds all of a sub-level's blocks, so sight is a ray in the server
level from the spot's eye height to the block's centre, stopping at outline
shapes. A spot sees the block when the first shape the ray meets is the
block's. The candidate spots are tested nearest first, so the cost stays at
one ray per spot up to the first that sees it. `dist` is measured in the plot
too; the sub-level moves as one piece, so distances are the same in the world.
Outline shapes, not light, decide sight, so glass hides a block: that is the
line a click needs.

### D6. `sable:tp` moves to the client thread

`here` reads the crosshair, which only the client has. `sable:tp` becomes a
client-thread action that needs the server. It resolves the sub-level on the
client, or by UUID or name on the server when the client lacks it, then does
the server part (finding the block, picking the spot, teleporting) in one task
submitted to the server thread, as `Sides.read` does, and waits two ticks for
the client to see the new place. The block search reuses `sable:find`'s scan
and its nearest-to-the-player order.

### D7. Small replies

- `find` compares the asked radius with the maximum before cutting it, and
  adds `radius` to the result as `sable:list` adds `server_only`.
- `since=` takes an int or a duration; a duration is turned into a tick by one
  helper the three commands share: the current tick minus its ticks.
- A single `screenshot` adds the `tick` its frame row already holds.
- `grid` writes `Regions`' palette line and its symbols, one per cell.

## Risks / Trade-offs

- [Scripts that relied on `~` at the spawn] → rare and easy to rewrite with
  `execute positioned`; `help server` and the README say where the default
  source is.
- [`use` on a world block now fails where it used to click what was in front]
  → the error names what is in the way, and `any` keeps the old behaviour.
- [An obstructed `use` takes half a second to fail] → the same as aboard, and
  short next to the detour a wrong click costs.
- [A client-thread `sable:tp` blocks the client while the server works] → one
  short task, with `Sides.read`'s five-second limit.
- [`grid` output changes] → no test or tool parses it; the README shows the
  new form.

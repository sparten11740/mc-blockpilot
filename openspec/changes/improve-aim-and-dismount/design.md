# Design

## Context

- `camera at` resolves the camera point and the look point as given. The look
  point is the player's eyes without `look`, an entity's centre, or a
  position. Plot positions follow their sub-level through `rendered()`.
  `clearPoint(level, from, to)` steps from the camera point toward the look
  point. That is only meaningful when both are in the same frame.
- `SubLevels` already offers `at` (the sub-level whose plot holds a cell),
  `toWorld` (a plot point where it is now; any other point as it is) and
  `toPlot` (a world point in a given sub-level's plot).
- `face` aims each tick at `point(...)`. For a plot point that is where the
  sub-level will be at the next pick, with a lead when the player isn't
  carried by it. It answers with `hit()`, which says `aim:nothing` when the
  crosshair hits nothing.
- `jump` is `pulse(keyJump, 2)`, which holds the key for one tick. `sneak` is
  `toggle(keyShift, on|off)`. The client sends the shift state to the server
  once a tick, and the server dismounts a passenger whose shift is down when
  it ticks.

## Goals / Non-Goals

**Goals:**
- Answer "is it in reach?" from the `face` reply alone.
- Dismount in one command.

**Non-Goals:**
- A `dismount` command, or a sneak tap that waits for the vehicle to be left.
  The tap is also useful elsewhere, and `status` shows `riding:`.
- `reach=` in `status`. It has no target to measure to.
- Testing world terrain while `clear` steps through a plot. The camera rides
  the sub-level, so the sub-level's own blocks are the ones in the way.

## Decisions

### D1: `clear` steps in the camera point's frame

Before stepping, the look point goes into the camera point's frame. With the
camera point in sub-level S's plot, a look point already in S's plot stays as
it is. Any other look point is turned into world coordinates and then into
S's plot. With the camera point in the world, the look point is turned into
world coordinates. Each check reads the block at the stepped cell, as now.

Alternative: step in world coordinates and turn every step back into the
plot. That needs more conversions and gives the same line, since the
sub-level is rigid.

### D2: `dist=` and `reach=` only on `aim:nothing`, not an error

`face` is also used to look at things far away, for a screenshot, so a
target beyond reach stays `ok`. The two fields
appear only when the crosshair hits nothing, the one ambiguous reply. A
reply naming the target or an obstruction already says what the agent
needs. `dist` is measured from the eyes to the point `face` aimed at on its
last tick. `reach` is the player's `blockInteractionRange()` for a position
and `entityInteractionRange()` for an entity, so attributes and mods that
change reach are honoured. Both are rounded to hundredths.

Alternative: `err out_of_reach`, as `use` does. Rejected because it would
break `face` as a way to look at distant targets.

### D3: The sneak tap holds for four ticks

Held for one tick, as `jump` is, the press and the release can reach the
integrated server before the same server tick, and the server never sees
shift down. Four ticks (a fifth of a second, a quick human tap) leave a full
server tick in between. The form gains an optional `state` keyword. Without
it, `sneak` runs the same pulse as `jump`, with five steps.

## Risks / Trade-offs

- [A server stall longer than the tap swallows it: the server reads the
  latest shift state it has received when it ticks] → A human's quick tap is
  lost the same way. `status` then still shows `riding:`, and a second tap
  does it. The live check dismounts from a minecart.
- [A plot camera point whose sub-level unloads between resolving and
  stepping] → `toPlot` gives null. `clear` then falls back to the look point
  as given, as before this change.

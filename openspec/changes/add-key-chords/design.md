# Design

## Context

- `key` resolves one word through `resolveKey` (mapping id, then key-name
  spellings with `_` or `.`) and drives it through `InputState`:
  `holdKey`/`releaseKey` for keys, `holdButton`/`releaseButton` for mouse
  buttons. `Injection` sends each event with the modifier bits of the keys
  held at that moment, so Ctrl or Shift held first already reaches the next
  key's event.
- No key name contains `+`; GLFW calls the keypad plus `keypad.add`.

## Goals / Non-Goals

**Goals:**
- One command for a key combination, released in full on success, failure or
  cancel.

**Non-Goals:**
- Sequences (`a, b`) or timing between the parts of a chord.

## Decisions

### D1: Split in `KeyNames`, resolve every part before pressing

`KeyNames.chord(word)` splits on `+` and rejects an empty part, as a pure
function with unit tests. `key` resolves every part, and checks that none is
the agent control toggle, before any event is sent, so an unknown part
presses nothing.

### D2: All down on the first tick, in order

A press sends the earlier keys' down events and then the last key's down
event on its first tick, in that order through the same `Injection` queue.
After the given ticks it sends the up events in reverse. Vanilla's F3 combos
test F3 when the second key goes down, and a key's modifier bits come from
the keys already held, so the order is all that matters.

Alternative: one tick per modifier before the last key. It is slower and
changes nothing the game can see.

### D3: Mouse buttons may be parts

A part that resolves to a mouse button (`left_control+mouse.left`) is held
and released through the button calls, as `key` does for a single button
now.

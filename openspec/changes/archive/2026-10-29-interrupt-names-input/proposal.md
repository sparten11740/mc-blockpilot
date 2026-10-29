# Proposal

## Why

When a person presses a key, clicks, scrolls or moves the mouse in the
focused game window, Blockpilot hands control back: the running action or wait
ends with `err interrupted` and the next reply carries `! interrupted player`.
Neither says what the input was. A test that ends with `err interrupted` in the
middle of a long run can't tell whether someone touched the game or something
else read as input, such as a cursor jump when a screen opened, without asking
around. Naming the input settles it from the reply alone.

## What Changes

- `err interrupted` gains fields naming the input that took control back:
  `input=key` with `key=<name>` (the name `key press` takes, e.g. `w` or
  `left_shift`), `input=button` with `button=<left|right|middle|n>`,
  `input=scroll`, or `input=move` with `dx=` and `dy=`, how far the cursor moved
  in window pixels.
- The `interrupted` event carries the same fields after `player`, e.g.
  `! interrupted player input=key key=w`.
- `help errors` describes the fields.

## Capabilities

### New Capabilities

None.

### Modified Capabilities
- `player-control`: an interrupt names the input that caused it.

## Impact

- `game/input/HumanInput.java` (describing the input), the 1.21.1 and 26.1
  `MouseHandlerMixin` and `KeyboardHandlerMixin` (passing the button and
  telling scroll from a click), `game/BlockpilotClient.java` (the error and
  event), `protocol/Engine.java` (stopping every script with a given error
  result), `protocol/Errors.java` (the description).
- A unit test for the names and fields the input gives.

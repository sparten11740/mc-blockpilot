# Design

## Context

The 1.21.1 and 26.1 `KeyboardHandlerMixin` and `MouseHandlerMixin` call
`HumanInput` at the head of the game's key, button, scroll and cursor handlers
whenever Blockpilot isn't injecting input itself. `HumanInput` checks that the
OS really has the window focused (and, for the cursor, that it moved more than
4 pixels) and runs one listener with no arguments. `BlockpilotClient` then
stops every script with `Result.error("interrupted")` through
`Engine.stopAll(code)` and broadcasts `! interrupted player`. Scroll goes
through the same call as a button, so the two can't be told apart today.

## Goals / Non-Goals

**Goals:**
- One description of the input, built where the input is seen, used for both
  the error and the event.

**Non-Goals:**
- Changing which input interrupts, or the 4-pixel threshold.

## Decisions

### D1: `HumanInput` describes the input as fields

The listener takes the `Fields` describing the input, which `HumanInput`
builds only when it is about to run the listener, so ordinary cursor events
cost nothing more. The mixins pass the button number, and scroll gets its own
`HumanInput.scroll`. The pieces that need no game classes (the button's name,
the move's rounding, the fields themselves) are static methods a unit test can
reach; the key's name comes from the game's `InputConstants` through
`KeyNames.agentName`, as `key press` resolves it. An alternative, a record
type per kind of input, would add a type only to be turned into fields.

### D2: Names as the commands take them

`key=` uses the name `key press` accepts (`w`, `left_shift`), and `button=`
the names `mouse` and `click` accept (`left`, `right`, `middle`), with other
buttons by the number the game gives them (`4` for the fourth). `dx` and `dy`
are whole window pixels of the move that crossed the threshold, with x to the
right and y down, as the game's cursor events report them.

### D3: Stopping with a full error result

`Engine.stopAll` gains an overload taking the `Result` to fail with, and the
string form calls it. The error carries the input fields; the event adds them
after `player`, so a reader matching `! interrupted player` keeps working.

## Risks / Trade-offs

- [The live check needs a person at the keyboard: an unfocused window never
  interrupts] → The fields are unit-tested; the live check asks the user to
  press a key, click and move the mouse in the game window during a wait.

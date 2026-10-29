# Tasks

## 1. Naming the input (player-control)

- [x] 1.1 Make `HumanInput` describe the input as fields for its listener (`input=key` with `key=`, `input=button` with `button=`, `input=scroll`, `input=move` with `dx=` and `dy=`), with `HumanInput.scroll` and the button number passed by both versions' mouse mixins (D1, D2); verify with a unit test of the button names (left, right, middle, then numbers) and of the fields for each kind of input, including the move's rounding
- [ ] 1.2 End scripts with `err interrupted` carrying those fields through an `Engine.stopAll` overload taking the error result, add them to the `interrupted` event after `player`, and describe them in `help errors` (D3); verify with `./gradlew build` for all four targets, then on the 1.21.1 dev client with the user pressing W, clicking and moving the mouse in the focused game window during `wait` commands, that each ends with `err interrupted` and the matching fields and that the next reply carries `! interrupted player` with the same fields

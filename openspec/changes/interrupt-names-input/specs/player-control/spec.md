## ADDED Requirements

### Requirement: An interrupt names its input
`err interrupted` and the `interrupted` event SHALL name the input that took
control back: `input=key` with `key=<name>`, the name `key press` takes for
that key; `input=button` with `button=left`, `right`, `middle` or the button's
number; `input=scroll`; or `input=move` with `dx=` and `dy=`, how far the
cursor moved in window pixels since its previous position.

#### Scenario: A key press
- **WHEN** the user presses W in the focused game window while an agent's
  `wait 200t` runs
- **THEN** the wait ends with `err interrupted input=key key=w`, and the next
  reply carries `! interrupted player input=key key=w`

#### Scenario: A mouse move
- **WHEN** the user moves the mouse to the right in the focused game window
  while an agent action runs
- **THEN** the action ends with `err interrupted input=move` and a positive
  `dx=`: how far the cursor moved in the event that took control back, which
  the game reports a few pixels at a time

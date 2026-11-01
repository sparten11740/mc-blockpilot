## ADDED Requirements

### Requirement: Key chords
In `key`, `+` SHALL join keys into a chord, each part a key name or key-mapping
id. A press SHALL hold the earlier keys in order, press the last for the given
ticks, then release all of them in reverse order. `key down` SHALL hold every
key of the chord in order, and `key up` SHALL release them in reverse. A chord
with an empty part SHALL fail with `err syntax` and press nothing.

#### Scenario: A debug combination
- **WHEN** an agent runs `key f3+b` in a world
- **THEN** hitboxes are shown, the debug screen stays closed, and no key is
  held once the command has answered

#### Scenario: Holding a chord
- **WHEN** an agent runs `key down left_shift+w`, waits, and then runs `key up
  left_shift+w`
- **THEN** the player sneaks forward in between, and afterwards stands still
  and no longer sneaks

#### Scenario: Empty part
- **WHEN** an agent runs `key f3+`
- **THEN** the result is `err syntax` and no key is pressed

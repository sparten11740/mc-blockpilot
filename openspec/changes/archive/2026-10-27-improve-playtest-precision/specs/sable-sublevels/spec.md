# Spec Delta

## MODIFIED Requirements

### Requirement: Teleporting next to a block aboard
`sable:tp <plot position>` SHALL put the player on a free spot next to that
block aboard, in a world with commands allowed, and answer with where the
player now stands and `dist`, from the player's eyes to the block's centre.
Of the free spots within two blocks it SHALL take the nearest from which the
block is in sight; when none sees it, the nearest, adding `hidden`. When no
spot within two blocks has room, it SHALL fail with `err no_room`.

#### Scenario: Onto the bridge
- **WHEN** an agent runs `sable:tp <plot position of the captain's seat>`
- **THEN** the player stands next to the seat aboard the ship, and
  `status` shows them riding it

#### Scenario: Inside the hull, not on it
- **WHEN** an agent runs `sable:tp <plot position of a lever>` inside a cabin
  whose window is next to the lever, with room both inside and on top of the
  window
- **THEN** the player stands inside the cabin, and the reply has `dist` but
  no `hidden`

#### Scenario: Block sealed in
- **WHEN** the block is walled in on every side, and the only free spot within
  two blocks is outside the wall
- **THEN** the player stands on that spot, and the reply carries `hidden`

## ADDED Requirements

### Requirement: Boarding next to a block found aboard
`sable:tp <sub-level> <block|#tag|*pattern>` SHALL board as `sable:tp <plot
position>` does, next to the matching block aboard that sub-level nearest the
player, and add that block's id as `block` and its plot position as `target`.
It SHALL search the server's copy of the plot, so it also boards a sub-level
the client has not received. When nothing aboard matches, it SHALL fail with
`err no_block`.

#### Scenario: Board a ship that has taken off
- **WHEN** a ship is flying away from the player and an agent runs `sable:tp
  <its UUID> *seat*`
- **THEN** the player stands next to a seat aboard it, and the reply names
  the seat's id as `block` and its plot position as `target`

#### Scenario: Nothing to board next to
- **WHEN** an agent runs `sable:tp here *door*` on a ship with no door
- **THEN** the result is `err no_block`, and the player has not moved

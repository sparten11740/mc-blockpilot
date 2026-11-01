## ADDED Requirements

### Requirement: The limit of blocks found aboard
`sable:find`'s limit SHALL be at most 1000, as `find`'s is, and a larger one
is cut to 1000.

#### Scenario: Every window aboard
- **WHEN** an agent runs `sable:find ship *window* 1000` on a ship with 360
  windows
- **THEN** the reply lists all 360 and carries no `count=`

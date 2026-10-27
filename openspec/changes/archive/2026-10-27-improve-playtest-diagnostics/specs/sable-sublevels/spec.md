# Spec Delta

## ADDED Requirements

### Requirement: Listing what the server holds
`sable:list [radius] side=server` SHALL list the integrated server's
sub-levels within the radius, with the same rows as the client's list; like
other `side=server` reads it needs cheats on. In singleplayer with cheats on,
the client's list SHALL add `server_only=<n>` when the server holds n
sub-levels within the radius that the client does not know.

#### Scenario: Client missed a sub-level
- **WHEN** the integrated server holds a ship within the radius that the client
  has not received, and an agent runs `sable:list`
- **THEN** the reply carries `server_only=1`, and `sable:list side=server`
  lists the ship

#### Scenario: Both sides agree
- **WHEN** the client knows every sub-level the server holds within the radius
- **THEN** `sable:list` carries no `server_only`

#### Scenario: No cheats
- **WHEN** an agent without cheats runs `sable:list side=server`
- **THEN** the result is `err no_cheats`, and the plain `sable:list` lists the
  client's sub-levels without a `server_only` note

## ADDED Requirements

### Requirement: An action when the script ends
`CommandContext.atEnd(Action)` SHALL run the action when the script the
command runs in ends, however it ends, on the client thread and before the
script's reply, after the endings registered before it. Called on a
context that isn't a script's, it SHALL throw
`UnsupportedOperationException`.

#### Scenario: Cleaning up after a failure
- **WHEN** an extension's command registers an ending and a later step of
  its script fails
- **THEN** the ending runs after the failing step's error and the skipped
  steps, before the reply is sent

#### Scenario: Outside a script
- **WHEN** code calls `atEnd` on a context that isn't a script's
- **THEN** it throws `UnsupportedOperationException`

### Requirement: What an ending answers
An ending's result SHALL arrive among the reply's events, or be pushed to a
session that pushes events, as an event named after the action's label,
with the result's fields, or `err` and the code; one that answers `skip`
SHALL leave no event. An extension's ending that hasn't answered after
60 s SHALL be cancelled and answer `err timeout`.

#### Scenario: The ending's event
- **WHEN** a script whose command registered an ending labelled `tidy`
  ends
- **THEN** the reply's events include `! tidy` with the ending's fields

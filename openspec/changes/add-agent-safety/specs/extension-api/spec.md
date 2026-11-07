# Spec Delta

## ADDED Requirements

### Requirement: Trusted players for extensions
`BlockpilotApi.isTrusted(UUID)` SHALL tell whether a player counts as
trusted, as agent-safety defines, so an extension that turns players'
words into agent input applies the same list. Extensions SHALL NOT be able
to change the list. It SHALL be an addition to the 2.1.0 API that leaves
2.0 integrations working.

#### Scenario: An add-on hears only trusted players
- **WHEN** the list holds only `Steve` and an extension asks `isTrusted`
  for `Mallory`'s UUID
- **THEN** it answers false, and true for `Steve`'s UUID

#### Scenario: Nobody trusted yet
- **WHEN** the list is empty
- **THEN** `isTrusted` answers false for every player but the agent's own

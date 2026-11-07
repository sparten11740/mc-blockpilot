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

### Requirement: Extensions are used only when allowed
An extension's commands and events SHALL be used only while its namespace
is in `allowedExtensions` in Blockpilot's config file, empty by default and
reloaded like the other safety settings; namespaces Blockpilot registers
itself, as its compat modules do, need none. A call SHALL fail before any
step with `err not_allowed <namespace>:<name>`, an event SHALL be dropped,
and `help` SHALL list the namespace marked as not allowed, naming the
setting.

#### Scenario: Calling a command that isn't allowed
- **WHEN** `mymod` registered `mymod:gear`, `allowedExtensions` lacks
  `mymod`, and an agent runs `mymod:gear`
- **THEN** the result is `err not_allowed mymod:gear`, and within a second
  of `mymod` being added to the file the command runs

#### Scenario: An event that isn't allowed
- **WHEN** `mymod` isn't allowed and emits `assembled`
- **THEN** no session gets `! mymod:assembled`

#### Scenario: Help names the setting
- **WHEN** `mymod` isn't allowed and an agent runs `help`
- **THEN** the `mymod` row says it isn't allowed and names
  `allowedExtensions` in `config/blockpilot/config.json`

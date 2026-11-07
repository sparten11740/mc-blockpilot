# Spec Delta

## MODIFIED Requirements

### Requirement: Extensions are used only when allowed
With `agentCommands` set to `play`, an extension's commands SHALL be used only
while its namespace is in `allowedExtensions` in the config file, empty by
default and reloaded like the other settings; namespaces Blockpilot registers
itself need none. A call SHALL fail before any step with `err not_allowed
<namespace>:<name>`, and `help` SHALL list the namespace marked as not allowed,
naming the setting. Under `all` every extension's commands SHALL be used, and
events SHALL always be delivered.

#### Scenario: Calling a command that isn't allowed
- **WHEN** `agentCommands` is `play`, `mymod` registered `mymod:gear`,
  `allowedExtensions` lacks `mymod`, and an agent runs `mymod:gear`
- **THEN** the result is `err not_allowed mymod:gear`, and within a second
  of `mymod` being added to the file the command runs

#### Scenario: Under all
- **WHEN** `agentCommands` is `all` and `allowedExtensions` lacks `mymod`
- **THEN** `mymod:gear` runs

#### Scenario: An event that isn't allowed
- **WHEN** `mymod` isn't allowed and emits `assembled`
- **THEN** every session gets `! mymod:assembled`

#### Scenario: Help names the setting
- **WHEN** `agentCommands` is `play`, `mymod` isn't allowed and an agent
  runs `help`
- **THEN** the `mymod` row says it isn't allowed and names
  `allowedExtensions` in `config/blockpilot/config.json`

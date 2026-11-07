# Spec Delta

## Purpose

Limits who can instruct an agent through the game and what an agent may do
there, so an agent that plays with other people can't be steered by just
anyone's words or turned against the world it plays in.

## ADDED Requirements

### Requirement: Dangerously allowing everything
`dangerouslyAllowAll: true` in Blockpilot's config file SHALL turn every
agent safety check off: every player's chat SHALL reach agents with its
text, on unverified servers too, `BlockpilotApi.isTrusted` SHALL answer
true for every player, every extension's commands and events SHALL be
used whatever `allowedExtensions` says, and `agentCommands` SHALL be
ignored as if `all`. It SHALL be false by default.

#### Scenario: An untrusted player's chat
- **WHEN** `dangerouslyAllowAll` is true and a player nobody trusted
  chats
- **THEN** agents get the message with its text

#### Scenario: Refusals lifted
- **WHEN** `dangerouslyAllowAll` is true, `agentCommands` is `play` and
  `allowedExtensions` is empty
- **THEN** `cmd say hi` and another mod's command both run

### Requirement: Allowing everything is set in the file only
`dangerouslyAllowAll` SHALL be set only in the config file, take effect
within a second of a change without a restart, and never be changed by a
command. A config file that can't be read SHALL count as false.

#### Scenario: Broken file
- **WHEN** the config file says `dangerouslyAllowAll: true` but can't be
  parsed when the game starts
- **THEN** every safety check stays on

### Requirement: Allowing everything is shown
While `dangerouslyAllowAll` is true, Blockpilot SHALL log a warning at
start and on each change of the file, `trust` SHALL report
`dangerously_allow_all`, and the agent activity indicator SHALL show
"safety off", hidden while recording like the rest of the indicator.

#### Scenario: Reading trust
- **WHEN** `dangerouslyAllowAll` is true and an agent runs `trust`
- **THEN** the reply carries `dangerously_allow_all`

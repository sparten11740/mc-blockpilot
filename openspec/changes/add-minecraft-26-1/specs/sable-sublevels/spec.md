# Spec Delta

## ADDED Requirements

### Requirement: Targets built without Sable
A target for a Minecraft version Sable had no release for when the target was
built SHALL ship without the Sable integration. With a Sable installed, it
SHALL run as without Sable, and the log and `help sable` SHALL say that this
Blockpilot build has no Sable integration.

#### Scenario: Sable on a target without the integration
- **WHEN** a Sable is installed beside a Blockpilot jar built without the
  Sable integration
- **THEN** the game starts, the log and `help sable` say that this build has
  no Sable integration, and the other commands work

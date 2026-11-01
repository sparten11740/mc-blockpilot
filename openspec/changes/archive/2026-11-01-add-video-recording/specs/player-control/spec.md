# Spec Delta

## MODIFIED Requirements

### Requirement: Agent activity indicator
A small HUD indicator SHALL show while an agent is present, the action running,
and when agent control is disabled. The action stays shown until 2 seconds
without one have passed, and the text changes at most twice a second, so agents
running short actions one after another don't make it flicker. While a
recording runs, the indicator SHALL NOT be drawn, so it stays out of the video.

#### Scenario: Agent acting
- **WHEN** an agent's `goto` is running
- **THEN** the HUD shows the active indicator with the action name

#### Scenario: Short actions in a row
- **WHEN** an agent runs one `server` command after another, a second apart
- **THEN** the indicator keeps showing `server` instead of switching back and
  forth with "agent active"

#### Scenario: Recording with the HUD shown
- **WHEN** an agent runs `record start` without `hud=off` and then `goto`
- **THEN** the video shows the hotbar but not the indicator, and the
  indicator is back after `record stop`

# Spec Delta

## ADDED Requirements

### Requirement: Readiness timeout says how long it waited
When `--wait-ready` runs out before the game is reachable, or before the
player is in a world with `--wait-world`, the CLI's stderr message SHALL say
how many seconds it waited, so an agent can tell a game that didn't start in
time from one that was never started.

#### Scenario: Game that never comes up
- **WHEN** an agent runs the CLI with `--wait-ready 300` and no game starts
- **THEN** the CLI exits with code 2 and its stderr message says it waited
  300 s

#### Scenario: Game left in the menu
- **WHEN** an agent runs the CLI with `--wait-ready 60 --wait-world` and the
  game stays in its title screen
- **THEN** the CLI exits with code 2 and its stderr message says it waited
  60 s for a world

### Requirement: Summary of failed steps
When steps of a script fail or are skipped, the CLI SHALL print one stderr
line counting them, and SHALL name the number of each failed step, counted
from 1 in the order of the reply's result lines.

#### Scenario: Error in the third step
- **WHEN** an agent runs the CLI with a five-step script whose third step
  fails
- **THEN** stderr says one step failed, step 3, and two were skipped

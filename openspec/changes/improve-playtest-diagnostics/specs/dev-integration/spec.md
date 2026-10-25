# Spec Delta

## ADDED Requirements

### Requirement: Readiness wait stops at a crash report
While `--wait-ready` waits for the endpoint, a crash report that appears in
`<gameDir>/crash-reports` after the wait began SHALL end the wait at once with
exit code 2, naming the report on stderr. A client that fails before any mod
loads, as one that cannot open its window, writes nothing Blockpilot can see,
so the wait then runs to its timeout.

#### Scenario: Mod crashes during start
- **WHEN** an agent runs the CLI with `--wait-ready 300` while the game starts,
  and the game crashes and writes `crash-reports/crash-<time>-client.txt`
- **THEN** the CLI exits with code 2 within about a second, naming that file

#### Scenario: Old crash reports
- **WHEN** the crash-reports folder already holds reports from earlier runs
- **THEN** they do not end the wait

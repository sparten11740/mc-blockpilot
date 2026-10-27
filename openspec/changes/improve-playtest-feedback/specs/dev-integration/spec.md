## ADDED Requirements

### Requirement: Reply timeout says what happened
When the game's reply to a script doesn't arrive within `--timeout`, the CLI
SHALL exit with code 2, and its stderr message SHALL say how many seconds it
waited, that the script was cancelled (closing the connection ends the
session, and with it the script), and that a longer script needs a larger
`--timeout`.

#### Scenario: Wait longer than the launcher's limit
- **WHEN** an agent runs the CLI without `--timeout` on a script whose `wait`
  has `timeout=540s`, and the wait doesn't end within 300 s
- **THEN** the CLI exits with code 2 after 300 s, and its stderr message says
  it waited 300 s, the script was cancelled, and to raise `--timeout`

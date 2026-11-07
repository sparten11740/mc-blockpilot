## ADDED Requirements

### Requirement: Usage errors in one line
A command line the CLI refuses as it reads its options and script SHALL get
exactly one line on stderr,
`blockpilot: <what is wrong>; see --help`, nothing on stdout, and exit code
2. The usage text SHALL be printed only for `--help` or `-h`.

#### Scenario: A script in several arguments
- **WHEN** an agent runs `blockpilot "status" "inv"`
- **THEN** stderr is the one line `blockpilot: only one script: put its
  commands in one argument, separated by semicolons; see --help`, and the
  exit code is 2

#### Scenario: Help
- **WHEN** an agent runs `blockpilot --help`
- **THEN** the usage text is printed to stdout, and the exit code is 0

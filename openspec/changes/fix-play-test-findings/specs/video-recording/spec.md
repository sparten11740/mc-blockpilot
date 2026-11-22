## MODIFIED Requirements

### Requirement: Recording files
A recording SHALL be saved as `<game dir>/blockpilot/recordings/<name>.mp4`.
Names use letters, digits, `_`, `.`, `+` and `-`, up to 64, and don't start
with `.`. Without a name it is `recording-<yyyyMMdd-HHmmss>`. A name whose file
exists SHALL fail with `err exists`, so a take is never overwritten.

#### Scenario: Name taken
- **WHEN** `recordings/lever.mp4` exists and an agent runs
  `record start lever`
- **THEN** the result is `err exists` naming the file, and nothing records

#### Scenario: Bad name
- **WHEN** an agent runs `record start ../lever`
- **THEN** the result is `err syntax` saying what names may contain

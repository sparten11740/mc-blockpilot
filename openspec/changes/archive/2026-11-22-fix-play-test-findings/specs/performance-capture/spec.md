## MODIFIED Requirements

### Requirement: Results as JSON and file
`perf stop` SHALL return the summary as a structured result (a compact text
overview in text mode, the full tree in JSON mode) and save the full JSON to
`<gameDir>/blockpilot/perf/<name>.json`. The per-tick timeline of server tick
times SHALL be only in the file. Capture names use letters, digits, `_`, `.`,
`+` and `-`, up to 64, and don't start with `.`; any other name fails with
`err syntax` naming it, and nothing is captured.

#### Scenario: Text overview
- **WHEN** a text-mode session stops a capture
- **THEN** the reply has one overview line (duration, MSPT, FPS, 1% low) and the
  top five entries of each list, plus the file path

#### Scenario: A name with a plus
- **WHEN** an agent runs `perf 10s own+bits`
- **THEN** the capture runs and is saved as `own+bits.json`

#### Scenario: A bad name
- **WHEN** an agent runs `perf start ../before`
- **THEN** the result is `err syntax` saying what capture names may hold and
  naming `../before`, and no capture runs

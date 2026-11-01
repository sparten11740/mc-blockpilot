## MODIFIED Requirements

### Requirement: Find counts what its limit left out
`find`'s limit SHALL be at most 1000, and a larger one is cut to 1000. When more
matches are found than the limit lets it list, the result SHALL carry
`count=<n>`, the number of matches in all; when it lists them all, it carries
no `count`.

#### Scenario: More matches than the limit
- **WHEN** an agent runs `find #logs 32 3` with eight logs loaded within 32
  blocks
- **THEN** the three nearest logs are listed and the result carries `count=8`

#### Scenario: All matches listed
- **WHEN** an agent runs `find #logs 32 10` with eight logs loaded within 32
  blocks
- **THEN** the eight logs are listed and the result carries no `count`


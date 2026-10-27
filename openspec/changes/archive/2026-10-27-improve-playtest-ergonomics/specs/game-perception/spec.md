# Spec Delta

## ADDED Requirements

### Requirement: Find counts what its limit left out
`find`'s limit SHALL be at most 100, and a larger one is cut to 100. When more
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

### Requirement: Screenshot width as captured
`screenshot`'s width SHALL be at most 1280, and, down to 16 pixels, never
more than the frame's or the crop's own width; with `sheet` it is each tile's
width. When the image is narrower than the width asked for, or than 512 when
none is, the reply SHALL carry `width=<n>`, the width it has; otherwise it
carries no `width`.

#### Scenario: Width over the cap
- **WHEN** an agent runs `screenshot 2560` with frames 2560 pixels wide
- **THEN** the image is 1280 pixels wide and the reply carries `width=1280`

#### Scenario: Width as asked
- **WHEN** an agent runs `screenshot 640` with frames 1280 pixels wide
- **THEN** the image is 640 pixels wide and the reply carries no `width`

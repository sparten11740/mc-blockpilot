## ADDED Requirements

### Requirement: Cameras on a sub-level's origin
With Sable, `sub:<uuid|name|here>` SHALL name a sub-level by its origin, where
each frame draws it, as `camera follow`'s subject, `camera orbit`'s centre,
and the look target of `camera at` and `camera move`. Following one SHALL keep
the camera's offset in the world's axes, as given with `at` or as it is. An
unknown or ambiguous reference SHALL fail as in the `sable:` commands, and any
`sub:` target without Sable with `err unsupported`.

#### Scenario: A chase shot that keeps its side
- **WHEN** an agent runs `camera follow sub:ship at ~0,8,-20` while the ship
  flies and turns
- **THEN** the camera stays 8 blocks above and 20 north of the ship's origin
  as it is drawn, looking at it, however the ship turns

#### Scenario: Orbiting a falling ship
- **WHEN** an agent runs `camera orbit sub:ship by 90 over 40t` while the
  ship falls
- **THEN** the camera keeps its distance across from the ship's origin and
  its height above it while it circles

#### Scenario: Turning is for entities
- **WHEN** an agent runs `camera follow sub:ship turning`
- **THEN** the result is `err syntax`, saying a camera at a plot position
  turns with the sub-level

#### Scenario: Without Sable
- **WHEN** an agent runs `camera follow sub:ship` where Sable isn't installed
- **THEN** the result is `err unsupported`, naming Sable

### Requirement: Camera moves round a sub-level
With `around sub:<ref>`, `camera move` SHALL take its relative (`~`) point,
via points and look as offsets in the world's axes from the sub-level's
origin in each frame, and absolute and plot positions as without it, so a
path keeps its shape round a moving sub-level and ends at that offset.

#### Scenario: Round a falling ship
- **WHEN** an agent runs `camera move ~0,5,-12 via ~12,5,0 look ~0,0,0
  around sub:ship over 40t` while the ship falls
- **THEN** the camera ends 5 blocks above and 12 north of the ship's origin
  as it is then drawn, looking at it

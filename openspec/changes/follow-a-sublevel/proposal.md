# Proposal

## Why

Filming a ship in flight needs a camera that keeps a fixed offset from it in
the world's axes: a chase or side shot that holds its side while the ship
banks and turns. Today a camera at a plot position rides in the ship's plot,
so it pitches, rolls and yaws with it; a camera at a world position stays
put; `camera follow` takes only entities, which sub-levels aren't. A path
round a flying ship has the same problem: its points are fixed in the world
or turn with the ship.

## What Changes

- `sub:<uuid|name|here>` names a Sable sub-level by its origin, the
  position its pose gives it, where each frame draws it.
- `camera follow sub:<ref> [at <~dx,dy,dz>]` keeps the camera at that offset
  from the origin in the world's axes, looking at it; without `at`, the
  offset it has now. `turning` stays for entities.
- `camera orbit sub:<ref> …` circles the origin as it moves, and
  `look sub:<ref>` aims `camera at` and `camera move` at it.
- `camera move … around sub:<ref> over …` makes the move's relative (`~`)
  point, via points and look offsets in the world's axes from the origin,
  in each frame, so a spline round a flying ship keeps its own heading.
- Without Sable, a `sub:` target answers `err unsupported`; an unknown one
  `err no_sublevel`, an ambiguous name `err ambiguous`, as the `sable:`
  commands do.
- The grammar gains the `sub-level` slot type, and the extension API the
  slot type `ArgType.SUB_LEVEL`, as blockpilot-api 2.2.0.

Out of scope: smoothing a follow, a timeline of camera shots (a change of
its own), and following a sub-level's rotation (a plot position does that).

## Capabilities

### New Capabilities

None.

### Modified Capabilities
- `sable-sublevels`: cameras follow, orbit, look at and move round a
  sub-level's origin.
- `command-language`: the grammar's `sub-level` slot type.
- `extension-api`: the `ArgType.SUB_LEVEL` slot type, in API 2.2.0.

## Impact

- **API:** `ArgType.SUB_LEVEL` and `Lexicon.isSubLevel`; `api_version`
  2.2.0. Mods built against 2.0.0 or 2.1.0 keep working.
- **Camera:** `CameraCommands`, `CameraFollow` (a world-axis follow of any
  point), `CameraControl` (a sub-level's origin as drawn).
- **Docs:** help, docs/commands.md, docs/sable.md, docs/extending.md,
  docs/grammar.ebnf and .xhtml, CHANGELOG under 1.4.0.

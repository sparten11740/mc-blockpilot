# Proposal

## Why

Filming a character on the move means the camera has to go with it. Today a
script can only guess the path, give it to `camera move` and hope the timing
matches the walk, and a look target turns the camera without moving it, so
the character soon walks out of the shot or shrinks into the distance.
Circling a set or a character is just as awkward: via points on a circle
give a curve that only comes close to one, and a circle round something that
moves can't be written at all.

## What Changes

- `camera follow <entity> [at <~dx,dy,dz>] [turning]` places the camera at an
  offset from the entity and keeps it there in every frame as the entity
  moves, looking at it as `camera at … look <entity>` does. Without `at`,
  the offset is the one the camera has from the entity now, so a shot framed
  with `camera at … look <entity>` carries on as the entity walks off. With
  `turning`, the offset turns with the way the entity faces, as Minecraft's
  `^` coordinates do (`x` to its left, `z` ahead), and follows a turn with a
  short lag, so `camera follow @pip at ~0,2,-6 turning` films over its
  shoulder from wherever it heads. Like `camera at`, it lasts until the next
  `camera` command; `stop` leaves it in place.
- `camera orbit <pos|entity> [by <degrees>] over <duration> [background]
  [ease=]` circles the camera round a point or an entity, which it follows as it
  moves, from where the camera is drawn now, keeping its distance and height and
  looking at the centre, for an entity the point `camera at … look` takes: a
  full turn without `by`, to the camera's right for a positive angle and to its
  left for a negative one. It is timed as `camera move` is: it answers when it
  ends, or at once with `background`, and `camera wait`, `stop`, real input and
  a newer `camera` command treat it as they treat a move.
- A camera placed with `away` stays away through `follow` and `orbit`, as it
  does through a move, so the server's world goes along with a journey.
- The help, `docs/commands.md`, `docs/grammar.ebnf` and its diagrams, the
  README and the 1.1.0 section of `CHANGELOG.md` show both forms.

It builds on two changes not yet archived: `camera-moves-in-the-background`
for `background` and `camera wait`, and `entities-by-name` for the `@<name>`
its scenarios use.

## Capabilities

### New Capabilities

None.

### Modified Capabilities
- `client-observation`: following an entity and orbiting with the camera.

## Impact

- `game/observe/CameraCommands.java`, new: the `camera` command, moved out
  of `ObservationCommands`, which had grown to screenshots, sounds,
  messages and particles besides, with the `follow` and `orbit` forms.
- `game/observe/CameraMotion.java`, new: what a timed camera motion shares,
  taken out of `CameraMove` (its clock, the background, the end, a stop);
  `CameraMove` keeps its path and `CameraOrbit`, new, the circle.
- `game/observe/CameraFollow.java`, new: the placement that keeps an offset
  from an entity, with the turning heading.
- `game/observe/CameraControl.java`: the motion under way is any timed
  motion, not only a move.
- `tools/smoke_test.py`: checks that a follow keeps its offset from a moved
  entity, that `turning` turns it, and that an orbit keeps its distance,
  ends by its angle and runs in the background.
- `docs/commands.md`, `docs/grammar.ebnf`, `docs/grammar.xhtml`, `README.md`,
  `CHANGELOG.md`.

# Proposal

## Why

A filmed sequence is a list of shots: a cut, a hold, a move, an orbit, a
follow. Today each segment is its own `camera move … background; camera
wait`, so a script that also flies a ship or fires a machine has to weave
its actions between the camera's steps, and the timing of the shot depends
on how long those actions take. A shot list that plays on its own clock,
started once, lets the script do its work while the camera films it.

## What Changes

- `camera play <shot> [| <shot>]…` plays the shots one after another on the
  camera's clock and answers at once, `shots=<n> over=<ticks>t`.
- A shot is a `camera at`, `move`, `orbit` or `follow` written without
  `camera`, with its own `ease=`, `fov=` and `roll=`, or `hold <duration>`.
  Each starts as its command would then, from where the camera is drawn:
  an `at` is a cut, a `follow` lasts until the next shot, and `~` positions
  count from the player as each shot starts. Sub-levels, entities and
  `away` work as in the commands.
- All shots are checked before the first starts; a bad one fails the play
  with `err syntax`, naming it.
- `camera wait` answers at the end of the last shot, or with the error of a
  shot that couldn't start. Another camera command, `stop`, real input or a
  reset end the play.
- `record script` with `camera wait` at its end films a play whole; the
  help and docs show it.

Out of scope: keyframes interpolated across shots (each shot keeps its own
path and look), recording without repeated frames, and a play outliving the
world.

## Capabilities

### New Capabilities

None.

### Modified Capabilities
- `client-observation`: the camera plays a shot list on its own clock.

## Impact

- **Camera:** `CameraTimeline`, the `play` form in `CameraCommands`, and
  `CameraControl` ending a play on stop and reset.
- **Protocol:** `CommandRegistry.parse`, which matches a step against a
  command's forms without running it, for a command that takes others as
  text.
- **Docs:** help, docs/commands.md, docs/recording.md, docs/grammar.ebnf and
  .xhtml, CHANGELOG under 1.4.0.

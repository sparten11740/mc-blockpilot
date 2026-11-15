# Design

## Context

A camera command places a `Placement` at once or starts a `CameraMotion`,
which follows the camera's clock (client ticks) and, in the background,
places its end when its time is up (`CameraControl.tick`). Its handler
resolves its points and reads where the camera is drawn as it runs.
`camera wait` waits for a motion under way.

## Decisions

### D1: Shots as text, read with the camera's own forms

`play` takes the rest of the step as text and splits it at `|`. Each shot is
read with `CommandRegistry.parse`, which matches `camera <shot>` against the
camera command's forms through the parser that checks help examples, so a
shot takes exactly what the command takes, options included: inside a text
slot, `ease=in` stays the shot's. `hold <duration>` is the play's own.

Alternatives: a `then` flag on each camera command, queuing it after the
one under way, needs no parsing but spreads one shot list over many steps;
a keyframe syntax of its own would duplicate what the forms already say.

### D2: Each shot runs its form when it starts

The play keeps each shot's arguments and runs its form's handler when the
shot starts, so it sets off from where the camera is then drawn and
resolves `~` against the player then, as the command would. Moves and
orbits get `background`, so the camera's clock places their end; the play
starts the next shot on the tick the one under way has ended. A cut or a
follow ends at once, a hold after its ticks.

### D3: Entity names looked up as the command would

The engine turns `@<name>` into an id before a handler runs; a shot's
arguments keep the name, so the camera looks it up through the same finder
the engine uses, handed down from the game package, at the play to fail
early and again as the shot starts.

### D4: Ending a play

Any camera command but `wait` ends a play before it runs; `stop`, real input
and a reset end it in `CameraControl`. A shot whose handler fails, such as
a follow whose entity is gone, ends the play, and `camera wait` answers its
error.

## Risks / Trade-offs

- [A look changes direction at the joins between shots] → each shot keeps
  its own look; a single `move` with via points keeps one smooth path.
- [A shot's syntax error counts columns in the command it was read as] →
  the play names the shot instead, without the column.

# Spec Delta

## ADDED Requirements

### Requirement: The hit a click sends
A `use` SHALL report, as `aim` and `hit=u,v,w`, the crosshair hit its click
was sent to the server with, read on the tick the game handled the click. That
hit is where the look ray, aimed at the target, first meets a block's shape,
so with `at <u,v,w>` it differs from the point asked for when the block's
shape does not hold that point on the ray.

#### Scenario: Point off a thin block's shape
- **WHEN** an agent runs `use <pos of a button> at 0.5,0.9,0.5`, above the
  button's shape
- **THEN** the reply's `aim` and `hit=` name where the aimed ray met a shape,
  which is the hit the server received

#### Scenario: Moving target
- **WHEN** an agent runs `use <plot position> any` on a block of a moving
  sub-level
- **THEN** the reported hit is the one of the tick the click was handled, not
  of the tick before

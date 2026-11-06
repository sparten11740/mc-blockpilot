# Design

## Context

- `CameraMixin` (one per version) puts the camera where the placement says
  and turns it with `setRotation(yaw, pitch)` each frame; the rotation is a
  quaternion the renderer, particles and the frustum read.
- The world's projection takes its field of view from
  `GameRenderer.getFov` on 1.21.1 and from `Camera`'s own field of view on
  26.x, the player's setting times sprint, potion and fluid effects.
- `status` reports the rendered angles through
  `CameraControl.recordRendered`, whose roll is the loader's camera roll:
  NeoForge's own field, 0 elsewhere. `pixels @<pos>` projects with the
  matrices the frame was drawn with, so it follows any field of view or
  roll.
- The view matrix is built from `Camera.rotation()` on both versions: 1.21.1
  reads it in `GameRenderer.renderLevel` after `Camera.setup`, 26.x conjugates
  it in `Camera.getViewRotationMatrix`. NeoForge 1.21.1 sets the rotation
  again in `renderLevel` from its camera-angle event, after `setup`.
- `camera-follows-and-orbits` adds `CameraMotion`, the base of every timed
  motion.

## Goals / Non-Goals

**Goals:**
- A placed camera's own field of view and roll, set at once or glided.

**Non-Goals:**
- Depth of field, lens distortion or any effect a shader would need.
- Changing the player's own field of view; `option fov` does that.
- Roll in the player's own view.
- A roll glide of more than half a turn: a glide takes the short way round,
  so a full spin is several motions.

## Decisions

### D1: A lens beside the placement

`CameraControl.Lens` gives the field of view and roll for a frame, as
`Point` gives a position; a null field of view means the player's. A
`Placement` gains a lens, the player's lens with no roll unless set, so the
existing places that build a placement keep their meaning. Placing anew
keeps the lens in use unless the command sets one, so a `camera at` after a
zoom keeps the zoom.

### D2: A motion glides the lens

`CameraMotion` reads the lens in use when it starts and, for each value the
command gives, interpolates from the old value to the new by the motion's
eased progress; a value it doesn't give keeps the old lens's. From the
player's field of view the start value is the player's current one, read
once, so the glide starts where the view is. Roll takes the short way
round.

### D3: Mixins apply it

For a placed camera whose lens has its own field of view, the world's
projection uses it: on 1.21.1 at the return of `GameRenderer.getFov(Camera,
float, boolean)` when the boolean is true, the world's field of view (the
hand's passes false), after NeoForge's field-of-view event; on 26.x at the
return of `Camera.calculateFov(float)`, which `Camera.update` stores as the
field of view the projection is set up with. The roll is applied, only while a
placed camera's lens has one, so the player's own view is untouched, at the
end of `Camera.setRotation`, on each version and on NeoForge's overload with a
roll, turning the rotation quaternion about its own forward axis, so every
place that sets the rotation, NeoForge's event included, keeps it, and the
view matrix, particles and frustum see it. `recordRendered` reports the
lens's roll for a placed camera, added to the loader's.

### D4: Ranges are checked before anything changes

`fov=` outside 1 to 170 and `roll=` outside -180 to 180 fail with `err
syntax` naming the range, before the camera moves, and on `camera reset`,
`off` or `wait` either option is `err syntax`, as `ease=` is off `move`.

## Risks / Trade-offs

- [Another mod also rolls the camera] → Blockpilot's roll applies after the
  game's rotation, on top of whatever a mod did before it; a mod that rolls
  later wins.
- [A very narrow field of view shows a far away world the client hasn't
  loaded] → Chunks still load by the player or an away camera; `settle`
  and `away` serve as they do for any far shot.

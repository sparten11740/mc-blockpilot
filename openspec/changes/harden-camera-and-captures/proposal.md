# Proposal

## Why

A review of the unreleased 1.4.0 work found bugs in `camera play` and in
clean frames that should not ship. Some only fail a command. Others crash the
game or leave the HUD hidden for good. Most come from three structural
shortcuts:
- each camera form checks and starts in one step, so a play can't check a
  shot without starting it;
- entity names are resolved outside the protocol layer;
- clean frames write the very options they change.

The public `sub:` slot type also hands add-ons raw text they can't resolve.
Once API 2.2.0 ships, that stays until an API 3.0.

## What Changes

- `camera play` checks every shot as its command would, except for what can
  change while it plays, before anything changes. A rejected play leaves the
  camera, and any play already running, as they were. The first shot failing
  to start answers that error, with nothing left playing.
- A shot that fails later ends the play however it fails, never reaching the
  game loop. Its error goes to the next `camera wait` of that play only.
- `sub:<name|here>` arguments are resolved to `sub:<uuid>` by the engine
  before a command runs, as `@names` become `e<id>`. Add-ons read them with a
  new `Args.subLevel()` returning the UUID; this is part of API 2.2.0.
- Clean frames:
  - hide the player's night vision, blindness, darkness and nausea only from
    what is drawn, so game logic still sees them (a blinded player can't
    sprint);
  - hold the field of view still without writing `fovEffectScale`;
  - keep the hand hidden through F1 for the whole capture;
  - also skip other mods' overlays on 1.20.1 Forge.
- While a capture shows the HUD its own way, `option hud` reads and sets the
  HUD as the capture will leave it, so restoring options can't leave the HUD
  hidden or the field of view effects at 0.
- A camera following a sub-level, or placed at a plot position, stays where
  it was last drawn once the sub-level leaves the client, as it does for a
  removed entity.
- `camera move` answers with its end as given again, a plot position
  included (a regression from 1.3.1).
- `flicker=none` means no rhythm of at least 1 luma repeats at least twice
  in the frames read. A single switch-on no longer reads as a slow flicker,
  and `err no_frames` is a listed error code.
- `drawn=` uses the client tick count that sample rows use.
- Help, docs and the changelog say what the code does: `option hud clean`,
  the screenshot label and fields, camera `shots=` and `over=`, `particles`
  totals with `match=`, and API 2.2.0 in docs/extending.md.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `client-observation`:
  - what a shot list checks up front and how it ends;
  - the hud option's clean value and how it behaves during a capture;
  - clean frames leaving game logic alone;
  - the rule for `flicker=none`, and `err no_frames`;
  - the clock `drawn=` counts on.
- `sable-sublevels`: `sub:` references resolved before a command runs, and
  cameras that stay put once their sub-level is gone.
- `extension-api`: the sub-level slot type, resolved by the engine and read
  with `Args.subLevel()`.

## Impact

- Code:
  - camera: `CameraCommands` split into one form table with separate check
    and start steps, a pure shot-list parser, and `CameraTimeline`;
  - protocol: `EntityNames` becomes reference resolution for entities and
    sub-levels, with `GameBridge.resolveSubLevel`, structured syntax errors
    from `CommandRegistry.parse`, and a step reader for `camera play`;
  - HUD: one capture owner replaces the save, apply and restore code in
    `ObservationCommands` and `Recording`, and `ClientOptions.Hud`;
  - mixins: `CleanEffectsMixin` scoped outside the client tick, a hook on
    `Options.fovEffectScale()`, and a `@Pseudo` mixin on Forge's `ForgeGui`;
  - flicker: `Flicker` and `PixelCommands`, plus `Errors.CODES`.
- API: `Args.subLevel(String)` returning `UUID`, in 2.2.0 (still
  unreleased); the meaning of `ArgType.SUB_LEVEL` values changes before its
  first release.
- No new dependencies.
- Ships in 1.4.0. The features it fixes were never released, so the
  changelog gets no "Fixed" entries for them.
- Overlaps with the open `exact-time-recording` change only in `Recording`'s
  HUD handling. Whichever lands second rebases.

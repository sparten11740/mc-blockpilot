# Design

## Context

See proposal.md for why. The code this changes:

- `CameraCommands` maps each form word to one handler that checks and acts
  in the same pass. Three sets (`EASED`, `PLACING`, `SHOTS`), hand-written
  error texts and the option descriptions repeat which form takes what.
  `camera play` parses each shot with `CommandRegistry.parse`, adds
  `background` after `over <duration>` by token, strips the parser's
  `col N:` prefix with a regex, and checks only option ranges and `@names`.
- `CameraTimeline` publishes a play before its first shot starts, catches
  only `CommandException`, and keeps a failure until some `camera wait`
  takes it.
- `@names` are resolved in `protocol/EntityNames` for script steps. Shots
  are text, so `CameraCommands` resolves them again through
  `ObservationCommands.EntityNames`, which `ObservationCommands` only passes
  on. `sub:` references are resolved inside the camera forms through
  `SableSupport`.
- Clean frames:
  - `HudVisibility.setClean` writes the real `hideGui` flag and the real
    `fovEffectScale` option.
  - `ClientOptions.Hud` reports a capture's clean state as the option value.
  - `screenshot` and `record` each save, apply and restore the HUD
    themselves.
  - `CleanEffectsMixin` hides four effects from every `hasEffect` and
    `getEffect` call on the local player.
- Version facts checked in the sources:
  - Options saving reads the `fovEffectScale` field, while every version's
    view code calls `Options.fovEffectScale()` (1.20.1, 1.21.1, 1.21.11,
    26.3).
  - Before 1.21.11, nausea's warp is drawn from
    `LocalPlayer.spinningEffectIntensity`, which the tick raises; from
    1.21.11 it comes from `getEffectBlendFactor`.
  - Sprinting checks blindness in `LocalPlayer.aiStep`.
  - On 1.20.1 Forge, `ForgeGui.render` overrides `Gui.render` without
    calling it.

## Goals / Non-Goals

**Goals:**
- A camera form's facts live in one place, and a new form is one entry.
- A shot list is checked by the same code that checks a camera command.
- Nothing a capture does is visible as an option change.

**Non-Goals:**
- `sable:` commands keep their own reference syntax and resolution; only
  `sub:` arguments move to the engine.
- No change to the motions themselves (`CameraMove`, `CameraOrbit`,
  `CameraFollow`) beyond what the gone-sub-level fix needs.
- Flicker keeps its periodogram, still on the client thread. With a 10 s
  cap, computing once is enough.

## Decisions

### 1. Camera forms as one table of checks and starts
Each form becomes a `Form` record that holds:
- its word;
- whether it places a camera (takes `fov=` and `roll=`);
- whether it is timed (takes `over`, `background` and `ease=`);
- a `check(Args)`;
- a `start(CommandContext, Args, Mode)`.

`check` covers everything knowable now:
- option ranges and durations of at least 1t;
- a relative follow offset, and no `turning` with a sub-level;
- `away`'s integrated-server and cheats gates;
- that the named entities exist.

`start` reads the world again and fails with `no_entity` when something is
gone. `camera` runs `check`, then ends a play, then `start`, so a rejected
command leaves a play running.

The shot forms, the forms that take `ease=`, and the error and option texts
naming them all come from the table. A play is the shot forms, plus `hold`.

Alternative: add the missing checks to `play()` by hand. Rejected: that is
how the current checks fell behind the forms.

### 2. A pure shot-list parser and a shot mode
`ShotList.parse(text)` splits on `|` and reads `hold <duration>`. It returns
holds and shot steps with no game classes, so it can be unit-tested. The play
reads each step as a `camera` command through the reference-resolving reader
(decision 4), then runs the form's `check`.

A shot starts in `Mode.SHOT`, where a timed form runs beside the play as
`background` would. No words are added to the shot's text, and the reply
never mentions a `background` the agent didn't write.

### 3. A timeline that publishes only a started play
- `CameraTimeline.play` starts the first shot before it publishes the play.
  If that start fails, the play answers the error and nothing is left
  playing.
- `tick` turns any exception into the play's failure: a `CommandException`
  as it is, anything else as `err internal`, logged with its stack. It never
  reaches the client tick.
- `stop` clears the failure too. Every accepted camera command but `wait`
  calls it, so an error is told to one `camera wait` at most.
- The timeline's state is `volatile`, and `next` checks it is still the play
  under way right before a shot starts. `Engine.stopAll` can stop it from
  outside the client thread.
- Holds and motions are timed through a small clock interface (ticks, and
  whether a motion runs), so the timeline is unit-tested without the game.

### 4. References resolved in the protocol layer (decision 2A)
- `protocol/EntityNames` becomes `References`. It resolves `@<name>` to
  `e<id>` and `sub:<name|here>` to `sub:<uuid>`, once per name per run,
  wherever script steps and queries resolve names now.
- The lookups move into a `References.Lookup` interface with `resolveEntity`
  and `resolveSubLevel`, which `GameBridge` extends. `resolveSubLevel` is
  implemented on the game side through `SableSupport`:
  - unknown names fail with `no_sublevel`;
  - shared names fail with `ambiguous`;
  - without Sable, references fail with `unsupported`.
- `Args.subLevel(name)` returns the `UUID`. Like `Args.entity`, it throws
  when handed an unresolved reference.
- `Parser.SyntaxError` keeps its column and reason apart.
  `CommandRegistry.parse` reports the reason alone, so `camera play` can
  name the shot without a regex.
- `ObservationCommands.EntityNames` goes away. The camera commands get a
  `Lookup` and read each shot as `References.resolve(registry.parse(step),
  lookup)`.

Consequence: a handler's own checks now come after resolution. Without
Sable, `camera follow sub:x turning` answers `unsupported` rather than
`syntax`, and its smoke test needs Sable.

Alternatives (see the proposal): parse-only references, or raw text and
documentation. Both leave every add-on to repeat name and `here` lookup.

### 5. HUD captures as layers over the option
`HudVisibility` keeps two layers:
- **The option layer:** hidden and clean, as `option hud` and F1 set them.
- **A stack of captures:** each with its mode (`HIDDEN` or `CLEAN`) and the
  option layer as it found it.

Captures:
- `HudVisibility.capture(mode)` returns a handle. `tick()` re-applies its
  mode each tick: the F1 flag for both modes, and clean for `CLEAN`, so F1
  can't bring the hand back. `end()` puts back the option layer it found.
- Captures nest. While any runs, `option hud` reads and writes the layer the
  outermost one will put back.
- Frames are clean while the option layer or any capture is clean, and the
  F1 flag is held on while any capture runs.

`ClientOptions.Hud` and its snapshot go through that layer, so a capture
never becomes an agent change. `ObservationCommands` and `Recording` hold a
handle in place of their own `State` and `Capture` code;
`Recording.hideHud` goes away.

Alternative: special-case `hud` and `fovEffectScale` in
`ClientOptions.set`. Rejected: two settings would stay coupled through one
real option.

### 6. FOV effects held through the accessor, not the option
While clean, an injection at the head of `Options.fovEffectScale()` hands
back a fixed instance at 0. The real option is never written:
- options.txt saving reads the field and keeps the player's value;
- Blockpilot's own `fovEffectScale` setting reads and writes the field
  through an accessor mixin.

The fixed instance uses the six-argument `OptionInstance` constructor, which
is the same in 1.20.1 and 26.3.

Alternative: hook each version's FOV code. Rejected: there are two to four
call sites, which move between classes across versions.

### 7. Effects hidden outside the client tick
A flag set and cleared around `Minecraft.tick()`, whose signature is stable
across versions, marks the client tick. `CleanEffectsMixin` filters only
outside it, so the tick's game logic sees the real effects:
- sprinting under blindness;
- Blockpilot's own reads at tick end.

Drawing sees no effects. Before 1.21.11, the nausea intensity fields are set
to 0 after each tick while clean, so the warp vanishes at once rather than
fading. From 1.21.11, the blend factor already reads the filtered effect.

Alternative: a flag around frame rendering. Rejected: `GameRenderer.render`
has a different signature in each version tree.

### 8. Forge's HUD class gets its own hook
A `@Pseudo` mixin on `net.minecraftforge.client.gui.overlay.ForgeGui`,
cancelling `render(GuiGraphics, float)` while clean, sits in the shared
mixin config. It applies only where that class exists (1.20.1 Forge), as
the Sable mixins do.

### 9. A gone sub-level holds its last drawn point
`CameraControl.drawn`, which `subLevelOrigin` goes through, keeps the point
where it was last drawn once its sub-level is no longer on the client,
instead of falling back to its plot coordinates. It starts from where the
sub-level lies when the command runs. `SubLevels.toWorldRendered` answers
null for a point in no sub-level the client draws, so `drawn` can tell.
Returning null to the placements instead was rejected: a placed camera's
position is read every frame and must never be null.

A `camera move` answers with its end point as resolved, a plot position
included, as in 1.3.1. Only a move `around` a sub-level answers where the
camera then rests.

### 10. Flicker: two cycles, computed once
- `Flicker.strongest` starts its frequencies at two cycles over the span,
  and keeps a rhythm only when the part swings across its mean, past a
  quarter of the rhythm's amplitude, at least three times (high, low, high,
  low). The floor alone isn't enough: a single step leaks into the higher
  frequencies, and read as a 1.4 Hz rhythm in a test. A quarter, not a
  half, still finds short flashes, whose fundamental is small beside their
  swing.
- `PixelCommands` builds each part's rhythm fields once and derives the text
  from them.
- The per-frame reader catches any exception, reporting `err internal` for
  the unexpected ones.
- `no_frames` joins `Errors.CODES`.

### 11. One client tick count
`drawn=` counts on the client tick counter behind sample rows'
`client_tick`, plus the frame's partial tick, instead of the camera's own
counter.

## Risks / Trade-offs

- [The accessibility screen shows the fixed 0 instance during clean frames,
  and a change made there is lost] → Only while clean, which agents set for
  captures. Noted in the design, not the help.
- [Effect queries outside the tick that aren't drawing, such as packet
  handlers, see the hidden effects] → Only the four visual effects, and the
  client makes no decision on them there.
- [An entity that never existed now fails the play itself, not
  `camera wait`] → Intended, and spec'd. The old scenario becomes one where
  the entity is removed during a hold.
- [`sub:here` and names are fixed when a command arrives] → Spec'd, and the
  same as `@names`.
- [`exact-time-recording` also changes `Recording`] → Whichever lands second
  rebases onto the HUD handle.

## Migration Plan

No migration is needed. API 2.2.0 and the camera, clean-frame and flicker
features are unreleased, so `ArgType.SUB_LEVEL` can change before its first
release. docs/extending.md moves to 2.2.0 and documents `Args.subLevel`.

# Design

## Context

See `proposal.md` for why. The figures come from the branch at `4f4b476`, using
scripts over its sources; nothing has been built from this design yet.

Three things constrain the approach:

- `add-minecraft-26-2-and-26-3` is finished and on local `main` at `4f4b476`,
  which this branch is based on.
- Mixin targets are strings the compiler can't check. With `defaultRequire: 1`,
  a wrong one stops the game when the class loads, so compiling isn't enough.
- `cli` runs on a bare JVM beside the jar (`cliUsesOnlyTheJdk`), and classes
  like `KeyNames` are unit-tested without the game's libraries.

## Goals / Non-Goals

**Goals:**
- Agents can't tell the difference on any of the eleven targets.
- A new 26.x release touches one adapter for each area that moved.
- Each boundary this adds is enforced by a test.

**Non-Goals:**
- A facade over the whole game. The 80 `Minecraft.getInstance()` calls stay, and
  a one-off API change in a command class stays an inline condition.
- Removing every rule keyed on a variable. See D2.
- Making a 26.2 or 26.3 node compile as the active node. `src/26.1` stays as
  checked in when the active node switches, so those nodes only build by their
  Gradle path, and this change leaves that as it is.
- Splitting other large classes, such as `PerfCommands` or `SableCommands`, or
  `AudioCapture`'s static state. They aren't causing version churn.
- Adding 26.4, a release, or changes to `dev.blockpilot.api`.

## Decisions

### D1. Share mixins from `src/main`; keep configs per tree

The 23 identical mixins move to `src/main/.../mixin`, keeping their class names.
`blockpilot.mixins.json` stays per tree, since it's what differs. This became
possible when the 26.x port sent these trees through Stonecutter, so the same
rules reach them.

The 7 near-identical ones move too if the merged file needs at most two
condition blocks; otherwise they stay. Past that limit, a merged file is harder
to read than two plain ones. `ConnectionMixin`, `SoundEngineMixin` and
`ParticleEngineAccessor` fit. `LibraryMixin`, `CameraMixin`,
`ClientPacketListenerMixin` and `MinecraftMixin` need three or more, so they
stay. Mixins that really differ also stay, so the trees shrink to 12 and 19
files. A test fails on any new byte-identical pair.

Unlike those trees, `src/main` is rewritten when the active node switches, so
the moved files must come back unchanged from a switch and back.

*Alternative:* a separate `src/mixin` tree. Rejected: it adds a source set to
solve what `src/main` already does.

### D2. Rename rules are for renames

A rule stays only if it swaps a name for a name. Three kinds leave:

- **Unused:** `.getNormal()`.
- **Changes a call's shape:** `pass.draw(0, 3)`, `pass.setPipeline(...)`,
  `pass.bindTexture(`, `renderer.countRenderedSections()` and the single-use
  `@Inject(method = "destroy",`. These become conditions.
- **Covered by an adapter:** the screen rules (D3) and the GPU rules (D5).

That takes rules keyed on a variable from 17 to 9. Those 9 stay: names such as
`.location()` are too common to match without the variable, and a miss is a
compile error on that node.

A new `RenameRulesTest` reads `stonecutter.gradle.kts` and fails if a rule has
no use, or if its new text appears in `src/main` or `src/1.21.1`. That second
check is the trap `docs/building.md` warns about, because rules run backwards
when switching versions. Switching rewrites `src/main` too, so the test skips on
any active node but the checked-in one.

### D3. `ClientScreens` for what moved in 26.2

`current()`, `open(Screen)` and `overlay()` hold the condition once, replacing
about 34 uses in 11 files and removing 5 rules. It follows `HudVisibility`:
small, single-purpose and named for what callers want. It lives in `game/common`
(D10), as `game/input` calls it too.

*Alternative:* add these to `Screens`. Rejected: that class describes a screen's
contents for replies and is private to `game`.

### D4. `WindowSystem` for GLFW and SDL

`game/input/WindowSystem` holds focus, the key's other code, button numbers, the
modifier table and the keyboard key type. `Focus`, `Injection`, `KeyNames` and
`HumanInput` call it, and a test allows `org.lwjgl.glfw` and `org.lwjgl.sdl`
imports only there and in mixins. `MouseHandlerMixin` and `InputConstantsMixin`
keep their conditions, because a mixin's signature has to match its target.

Native calls stay inside method bodies and constants stay compile-time, so
`KeyNamesTest` still loads the class without the game's libraries.

### D5. `Gpu` for the 26.x capture path

In `src/26.1`, `Gpu` holds mapping a buffer, creating a texture, the blit, pixel
size and the waits on fences. `FrameCapture` and `FrameReadback` keep what
they're for: reading a frame and pacing a recording. The main render target goes
in `FrameCapture`, which both trees have, because `RecordCommands` in `src/main`
needs it too. Rules for moved packages stay, because Java has no other way to
switch imports. `src/1.21.1` is unaffected, since it has no such API.

### D6. Close the loader seam

- `Hooks` gains `onRenderStageStart` and `onRenderStageEnd(String)`, and
  `platform.neoforge` stops importing `game.perf`.
- `Services` gains `cameraRoll(Camera)`, defaulting to 0, replacing the only
  loader condition in game code.
- `minecraftVersion()` moves into `ClientPlatform`, `isModLoaded` is built on
  `versionOf`, and Fabric and vanilla share category registration, as
  `ClientPlatform.registerCategoryOf`.
- `FabricServices` and `NeoForgeServices` leave the entry points, which keep
  only the event wiring.

Tests forbid `dev.blockpilot.game` imports in `platform.fabric` and
`platform.neoforge`, and loader conditions outside `platform`.

### D7. Shared build script through `apply(from = ...)`

`common.gradle.kts` takes the toolchain, encoding, licence files, test
dependencies, test inputs and publication. This is the pattern `version-sources`
and `coverage` already use, and each loader script keeps what's really its own.

*Alternative:* a `buildSrc` convention plugin. It's more idiomatic and gives
shared functions, but it adds a build layer for 475 lines of script. Each script
therefore keeps its short `prop` helpers.

### D8. `settings.gradle.kts` is the source of truth for targets

`test_targets.py` fails at the start of a run unless the targets it reads match
`versions/` exactly, and every Fabric and NeoForge target has a row in
`release.yml` with the right `minecraft_version`. The release job's expected jar
count comes from `versions/` instead of a literal 11. The rows stay written out,
as they also hold dependencies the settings don't know. In `docs/building.md`,
"To add a target" drops the step that updates the count and keeps the one that
adds a row, saying `test_targets.py` now fails without it.

### D9. Split by what the code is

- **`ActionCommands`** becomes `MovementCommands`, `InteractCommands`,
  `InventoryCommands` and `TalkCommands`, with shared helpers in
  `PlayerActions`. `GameCommands` registers them where `ActionCommands` was.
  Command order is what `help` shows, and the four groups are already
  contiguous, so it doesn't change.
- **`Perf`** stays the static API, so the 34 files using it don't change. Its
  state and logic move to a `PerfCapture` created at each `start`, replacing the
  field-by-field reset. `active` stays a static volatile, checked first, so a
  game with no capture running pays nothing new.
- **`Timed`** in `PerfCommands` and `RecordCommands` share a small base class.

### D10. A one-way dependency order in `game`

The order becomes `game` commands, then subpackages, then a new `game/common`
that imports no other `game` package. `Ids`, `Sides`, `Shapes`, `BlockScan`,
`BlockMatcher`, `GameClock` and `FrameCapture` go to `game/common`, with
`BlockFrame`, `BlockToken` and `ShapeBounds`, which they use, and the new
`ClientScreens`, `Gpu` and `TimedStop`. `Rel` moves to `game/sable` and
`Screenshots` to `game/observe`, because that's what they depend on.

`Json` and `McpTools` move from `cli` to a new JDK-only `dev.blockpilot.wire`,
so `protocol` stops importing `cli`. `game` may still use `cli.Launcher`, since
the game is what writes the launcher script. `ArchitectureTest` enforces all of
this and also starts scanning `src/1.21.1`, `src/26.1` and `src/vanilla`.

### D11. Proving that nothing changed

Before any code changes, record for each target: its jar's class list, manifest
and metadata; the full `help` output; and the JSON shapes. Every group then has
to pass the build on all nodes and `test_targets.py` on four targets that span
the range (`1.21.1-fabric`, `26.1-neoforge`, `26.3-fabric`, `26.3-vanilla`). The
end is a full run, with `help` and shapes compared against the baseline. Class
lists may differ only by this change's moves and new classes.

*Alternative:* a full run per group. Rejected: at about 25 minutes each, the
four targets catch nearly the same failures.

The groups shared runs in the end: 4 to 7 passed one run of six targets, and 9
to 11 the full run at `f846641`, as each run covered what its groups changed.

## Risks / Trade-offs

- [A mixin goes wrong only at load] → Class names and configs don't change, and
  the runs start a real client on each target.
- [Moving `Perf`'s state changes threading] → Keep its existing locks and
  `synchronized` methods as they are, and rely on `PerfTest` and the smoke
  test's `perf` checks.
- [Real-input paths have no automated test] → `WindowSystem` touches them, so
  Jan checked them by hand on 26.3 (task 12.2). Real clicks on 26.3 and all real
  input on 1.21.1 weren't seen.
- [`main` moves again] → Rebase before continuing.
- [A large diff to review] → Small commits, a group per decision, each
  revertible.
- [Other machines] → Windows and Linux stay untested here, as on the branch
  below.
- [The two-block limit in D1 is a judgement call] → It only decides whether a
  file moves. Either outcome behaves the same and can be changed later.

## Migration Plan

Merge into `main`. There's nothing to migrate for users or extension mods, since
`dev.blockpilot.api` and the mixin class names stay the same. Rollback is
reverting a group's commits.

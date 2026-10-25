# Design

## Context

Sable keeps a sub-level's blocks in a plot (a grid far out in the same
dimension) and draws them where the sub-level's pose puts them. Sable publishes
a small API for other mods, `sable-companion`:
- `SableCompanion.INSTANCE` answers the sub-level at a plot position, chunk,
  entity or block entity, and the sub-levels intersecting a box;
- it projects a point out of a plot into the world, gives velocities, and the
  sub-level a player stands on or rides;
- `SubLevelAccess` gives a sub-level's logical and last pose, bounding box,
  UUID and name; a client sub-level also has a render pose;
- `Pose3dc` transforms positions between plot and world, both ways.

Sable bundles the companion (jar-in-jar, range `[1.6.0,)`) and registers its
implementation through `ServiceLoader`; the companion's default implementation
answers "no sub-levels". For Minecraft 1.21.1, Sable 2.0.5–2.0.6 ship
`dev.ryanhcode.sable-companion:sable-companion-common-1.21.1:1.6.0`, published
on `maven.ryanhcode.dev`.

In Blockpilot today, `Rel` prints positions relative to the player, aiming
targets are fixed world points (`ActionCommands.point`), the camera placement
holds a fixed position, and `find` scans the loaded chunks around the player.
The mod metadata is generated from `src/main/templates`.

## Goals / Non-Goals

**Goals:**
- Sable is optional in every sense: no bundled classes, no dependency the loader
  enforces, no class loaded from it unless it is installed and compatible, and
  no way for a Sable API mismatch to stop the game.
- One place that touches Sable, so its API can change without touching the rest.

**Non-Goals:**
- Assembling, disassembling or steering sub-levels, or reading physics beyond
  pose and velocity.
- Pathfinding on a moving sub-level: `goto` to a plot position is refused;
  `sable:tp` is the way aboard.
- Sable on dedicated servers, and on loaders Sable doesn't ship for.

## Decisions

### D1. Compile against the companion API only, bundle nothing

- **What:** a `compileOnly` dependency on the companion artifact for each
  Minecraft version node, from `maven.ryanhcode.dev` (repository content
  filtered to `dev.ryanhcode.*`). The metadata gets no Sable dependency entry.
- **Why the companion:** it is the facade Sable's authors publish for third
  parties, so Sable's internals can change without reaching Blockpilot.
- **Why no metadata entry:** NeoForge refuses to start when an `optional`
  dependency is present but out of its version range. That is the failure this
  change must never cause, so compatibility is decided at run time instead (D3).
- **Alternatives:**
  - Compiling against Sable itself ties Blockpilot to Sable's internals and
    release cadence.
  - Bundling the companion (jar-in-jar) adds a copy that does nothing without
    Sable and raises a redistribution question.
  - Pure reflection works without a compile dependency but loses type checking
    for no gain.

### D2. One compat package behind a plain interface

- **The interface:** the rest of Blockpilot sees a `SubLevels` interface in
  plain Minecraft and Java types. It has a sub-level at a position, plot↔world
  transforms, the sub-levels in a box, the one the player rides, and a
  sub-level's info. The default implementation answers "none" and leaves
  positions as they are.
- **The implementation:** `dev.blockpilot.compat.sable` holds the only code that
  names companion types. It is loaded with `Class.forName` inside a
  `catch (LinkageError | Exception)`, and only after
  `ClientPlatform.isModLoaded("sable")` and the version gate. A failure while
  loading it leaves the default in place and logs once.
- **Commands:** the compat package also registers the `sable:` commands through
  `BlockpilotApi` (namespace `sable`, registrant `blockpilot`) during
  Blockpilot's own start-up, so they exist only when the integration is on.
- **Enforcement:** a unit test fails if any source file outside the compat
  package imports `dev.ryanhcode`, so the isolation can't erode.
- **Alternatives:** guarding individual calls in the built-in commands spreads
  Sable checks across many classes.

### D3. Different Sable versions

What can differ is the companion version on the classpath. Sable picks the
highest bundled copy among installed mods, and its API can change between
releases. The approach, in layers:

- **Compile target:** each Minecraft version node pins the companion release
  that Sable ships for it (1.6.0 for 1.21.1). A node without a Sable build
  compiles without the compat package and behaves as without Sable.
- **Version gate at start-up:**
  - Read the companion version actually loaded (its jar manifest's
    `Implementation-Version`), falling back to Sable's mod version.
  - Accept the major version compiled against, at or above the pinned minor:
    1.x from 1.6 on. Additions within a major don't break existing calls.
  - Anything else turns the integration off. The log and `help sable` name the
    version found and the range supported, and the game runs as without Sable.
- **Probe:** before switching on, a few harmless calls are made (resolving
  `INSTANCE`, asking whether a plot chunk exists). This catches an
  implementation that loads but doesn't link.
- **Run-time linkage guard:**
  - Every compat method catches `LinkageError` (`NoSuchMethodError`,
    `NoClassDefFoundError`, `AbstractMethodError`, …).
  - The first one fails that command with `err sable …` and switches the
    integration off until restart.
  - So a 1.x release that changed the API anyway degrades to "no Sable" instead
    of crashing.
- **Supporting a new major:** add the new artifact to that Minecraft node's
  build and widen the gate. If the API changed, the compat package gets a second
  implementation selected by version, and nothing outside it changes.
- **Tested versions:** the README records the Sable and companion versions the
  play-test ran against.
- **Testing the gate:** the system property `blockpilot.sable.version`
  overrides the detected version, so the "unsupported" path can be checked in
  game without another Sable build.
- **Alternatives:**
  - Pinning an exact Sable version breaks on every Sable patch.
  - Writing an adapter per Sable release costs more than one small, published
    API needs.

### D4. Plot positions print as themselves

- **Printing:** `Rel` asks `SubLevels` whether a block position lies in a plot.
  If it does, it prints the absolute plot position and adds `sub=<name>` and
  `world=<x,y,z>`. In JSON these become a `sub` field and a `world` field.
- **Reading:** a plot position given as an argument is used as is. The client
  holds the plot chunks of the sub-levels it tracks, and `side=server` reads the
  server's.
- **Names:** a sub-level's name comes from `getName()`, else the first 8
  characters of its UUID.

### D5. Aim and camera targets are suppliers

- **Aiming:** `ActionCommands.point` already returns a supplier. For a plot
  position it projects the block's centre through the sub-level's logical pose
  on every tick, so `face` and `use` track a moving sub-level.
- **Obstruction:** after aiming, `use` at a plot position compares the
  crosshair's block (Sable's ray hits report plot positions) with the target. A
  mismatch fails with `err obstructed <id> <position>`.
- **Camera:** the placement's position becomes a supplier too. A plot position
  projects through the client sub-level's render pose for the frame's partial
  tick, so a camera riding a moving ship doesn't jitter.

### D6. The `sable:` commands

- **References:** UUID, name, or `here`: the sub-level the player stands on or
  rides, else the one containing the crosshair's block.
- **`sable:list`:** the sub-levels intersecting a box around the player.
- **`sable:info`:** pose angles from the orientation quaternion, as Minecraft
  yaw, pitch and roll.
- **`sable:world` and `sable:plot`:** the logical pose's transforms.
- **`sable:find`:**
  - Scans the sub-level's plot-space bounds (its bounding box transformed back
    through its pose), with `find`'s section-palette pruning.
  - The scan is capped like `blocks` (at most 262,144 positions, else
    `err too_large`).
  - It runs on the client, or on the server with `side=server`.
- **`sable:tp`:**
  - Runs on the server thread and needs a world with commands allowed.
  - Searches the plot within two blocks of the target for two air blocks above
    one with a collision shape.
  - Projects that spot to the world and teleports the player there. Sable keeps
    a player standing on a sub-level aboard it.

### D7. Play-test fixes

- **Key names:** before looking a key up, words separated by `_` are tried with
  `.` as well (`left_shift` becomes `key.keyboard.left.shift`). An unknown name
  lists the three key names closest by edit distance.
- **`sample … while wait`:** the sample statement's `during` becomes a
  statement, either a call of an action or a wait. The runner starts a wait the
  way it starts one written alone, and the sampler runs beside it unchanged.
- **Camera reply:** names the block state at the camera's block position. It
  adds `inside=<id>` when that block's collision shape is a full block.
- **`blocks … grid`:** a second renderer over the same region read, one id per
  cell.
- **CLI:** after the reply, when any line is `err` or `skip`, one stderr line
  counts them, e.g. `blockpilot: 1 err, 5 skip (nothing after the error ran)`.
  The exit code rule stays as specified in `add-test-harness`.

## Risks / Trade-offs

- [A companion 1.x release breaks its API anyway] → The linkage guard turns the
  integration off at the first broken call, and the README states the tested
  versions.
- [Sable's ray hits might report world rather than plot positions] → The
  obstruction check compares in both frames, and the first play-test task
  confirms which.
- [The client lacks the plot chunks of sub-levels it doesn't track] → Reads fail
  with `err unloaded`, as for unloaded world chunks; `side=server` reads the
  server's copy.
- [Teleporting onto a moving sub-level lands where it is on that tick] → The
  spot is computed and used on the same server tick, and Sable carries a player
  who stands aboard.
- [Sable may not exist for Fabric] → The compat code compiles for both loaders
  but is verified on NeoForge only; on Fabric the integration stays off unless
  Sable is installed.

## Migration Plan

Nothing to migrate: no configuration or saved data. Rolling back means removing
the compat package and its registration; no other code depends on it.

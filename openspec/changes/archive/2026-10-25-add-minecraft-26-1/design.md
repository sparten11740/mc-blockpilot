# Design

## Context

See proposal.md for why. The current state these decisions build on:
- Stonecutter builds each `<minecraft>-<loader>` node from `blockpilot/src`.
  The sources are checked in for `1.21.1-neoforge`. Loader halves are split
  with `//? if neoforge` and `//? if fabric`, and the mixins live in
  `src/1.21.1/`. `stonecutter.gradle.kts` has a `rename` helper, unused so far.
- The archived first change listed what was expected to differ on 26.1 (D9
  there): input callbacks, recipe placement, screenshot capture, HUD drawing
  and the `ResourceLocation` rename. A first compile of the 26.1 NeoForge node
  shows these and more:
  - container clicks: `ClickType` became `ContainerInput`;
  - inventory fields are now private;
  - permissions: a `PermissionSet` replaces the level number;
  - game rules and some option types moved or were removed;
  - other changed signatures: disconnecting, `teleportTo`, build height,
    entity saving.
- 26.1 is the first unobfuscated release. Fabric builds it with plain Loom,
  without remapping, through the `loom-back-compat` plugin the Fabric build
  already applies. Its access widener names classes by their official names.
- Sable and its companion API exist for 1.21.1 only.

## Goals / Non-Goals

**Goals:**
- One source tree: code the versions share stays a single copy, and the
  1.21.1 jars behave as before, apart from `option reset` now also restoring
  options changed along with the one an agent set (D6).
- Every command and reply the same on 26.1, apart from the crafting gap in the
  `player-control` delta, the graphics options in the `client-observation`
  delta and the render timings in the `performance-capture` delta.

**Non-Goals:**
- Versions between 1.21.1 and 26.1, and newer 26.x releases.
- Sable on 26.1.
- A dedicated-server build.

## Decisions

### D1. Toolchain

- **Versions:** Minecraft 26.1.2, Java 25, NeoForge 26.1.2.124, and Fabric
  Loader 0.20.0 with Fabric API 0.155.3+26.1.2. The jar requires Fabric Loader
  0.18.4 or newer, as Fabric API does.
- **Mappings:** none on 26.1. Parchment applies only to nodes that set its
  properties, which the NeoForge build already checks. The Fabric build skips
  mappings on unobfuscated releases.
- **Tests:** the game-free unit tests run on every node, on that node's Java.

### D2. Where version differences go

Three tools, simplest first:
- **Rename rules** in `stonecutter.gradle.kts` for one-for-one renames, such as
  `ResourceLocation` → `Identifier`. A rule matches enough context (an import,
  a type with its variable) that it can't hit shared text by accident.
- **`//? if >=26.1` blocks** for calls whose shape changed, kept to the lines
  that differ.
- **Per-version classes** in `src/<version>/java`, with the same name and
  methods on every version, for code that is mostly version-specific: the
  mixins (their targets differ), the recipe lookup (D3) and the frame capture
  (D4). Shared code calls them the same way on every target.

Everything in `api` and `protocol`, plus pathfinding, parsing, conditions and
the Sable bridge's interfaces, stays one copy with no conditions.

*Alternative:* a copy of each changed class per version. Rejected: most
changes are a line or two in classes that are otherwise shared, and copies
drift apart.

### D3. Crafting on 26.1

Since 1.21.2 the client has no recipe manager. It knows only the recipe-book
entries the server sends, which are the unlocked recipes. The book places a
recipe by its display id, and the server places it only if it is unlocked.
- **Finding recipes:** in singleplayer, the integrated server's recipe manager
  supplies every crafting recipe for the item, with its ingredients and
  pattern, as the client's manager does on 1.21.1. On a remote server, the
  client's recipe book supplies the unlocked ones, with their ingredients.
- **Placing:** an unlocked recipe goes through the book, by its display id. A
  locked one is laid out click by click, as on 1.21.1.
- **The gap:** on a remote server a locked recipe is unknown, so the result is
  `err no_recipe`. `help craft` says so on 26.1.
- **The seam:** a per-version recipe lookup answers, for an item, the
  candidate recipes as a version-neutral record: ingredients in grid order,
  pattern width, whether it fits the 2x2 grid, the result count, and how to
  place it through the book, if it can be. `ContainerCommands` keeps the
  choosing, the table handling and the clicks.

### D4. Reading the frame on 26.1

The game's own screenshot on 26.1 copies the frame into a GPU buffer and hands
over the image in a callback once a fence says the copy is done, a frame or
more later.
- **Reading:** Blockpilot makes the same copy and maps the buffer right away,
  which waits for the copy to finish, as reading the framebuffer did on
  1.21.1. Captures stay synchronous, so `screenshot`, sheets, `pixels` and the
  MCP tool keep their timing.
- **The seam:** a per-version frame class reads the frame, encodes PNG and
  reads and draws pixels. On 26.1 the PNG comes from the game's own encoder,
  reached through an invoker, since only its file-writing form is public.
- *Alternative:* waiting for the callback. Rejected: it made every capture
  asynchronous, and `pixels` a multi-tick action, to save one stall per
  capture.

### D5. Targets without Sable

- **Build:** a node compiles the Sable compat package only if it pins a
  companion version (`sable_companion_version`). The 26.1 nodes don't.
- **Start-up:** with Sable installed but the compat package absent from the
  jar, the integration turns off before the version check. The log and
  `help sable` say "this Blockpilot build has no Sable integration", which
  points at the jar, not at the Sable version.

### D6. Graphics options on 26.1

26.1 replaced the graphics mode with a preset. Applying a preset sets a dozen
options (render distance, leaves, transparency, particles, clouds, …), and
changing one of those sets the preset to custom.
- **Names:** `graphicsPreset`, `cutoutLeaves` and `improvedTransparency`, as in
  26.1's `options.txt`, replace `graphicsMode`.
- **Side effects:** when an agent sets an option, every listed option whose
  value changed gets its original recorded, not just the one set. Unlisted
  options a preset sets are recorded through a hook on the preset's own setter
  (a 26.1 mixin), so `option reset` restores them too.
- **Reset order:** the preset first, then the other options in list order.
  Restoring the preset sets the options it covers to the preset's values,
  which are the player's own unless the preset was custom; restoring the
  others then changes nothing, or puts back the player's custom values.

### D7. Render timings on 26.1

26.1 renders through render states: a renderer's code runs when the frame
extracts its state and when it submits that state, and the drawing after that
is batched for everything of a kind.
- **Renderers:** block-entity and entity renderers are timed in both steps,
  per type. The extract step adds time only, and the submit step completes the
  call, so `calls` still counts one per render.
- **NeoForge's extra overload:** NeoForge adds an extraction overload that does
  the work, which vanilla's calls; the timing wraps the renderer call inside
  whichever overload makes it, so it isn't counted twice.
- **Particles:** a quad particle's render time is its extraction into the
  frame. Groups that draw particles their own way are not timed; quad
  particles are nearly all of them.
- **Render stages:** each NeoForge 26.1 stage is its own event class, named
  after the stage: `AfterTranslucentParticles` is
  `after_translucent_particles`.

### D8. Checking the port

- **Compile:** all four nodes compile, and their unit tests pass.
- **Run:** each 26.1 dev client starts, joins a test world, and passes the
  smoke test in text and JSON mode. The JSON shapes are compared with the
  1.21.1 jar of the same loader.
- **Clients:** dev clients are closed with `quit`. Only one that never opened
  its endpoint (stuck on a loading error) is stopped by its process id, and
  only Blockpilot's own dev client, found by its run folder.

## Risks / Trade-offs

- [26.1's input and rendering APIs changed most since 1.21.1, so the mixins
  may need new injection points] → Each mixin is ported against the 26.1
  sources, and the smoke test drives every hook it serves.
- [Rename rules can rewrite shared text by accident] → Each rule carries enough
  context to be unique, and both 1.21.1 nodes are rebuilt after each rule.
- [Mapping the copied buffer at once stalls the render thread until the GPU
  catches up] → One stall per captured frame, as on 1.21.1.
- [26.1 renders through render states, so render timings measure a renderer's
  extract and submit steps, not the batched drawing] → Recorded in the
  `performance-capture` delta; the drawing was never per type anyway.
- [Reading the integrated server's recipe manager from the client thread] →
  Recipes don't change after loading, apart from a `/reload`; a reload during
  a `craft` at worst makes that one craft fail.

## Migration Plan

Nothing to migrate. The 1.21.1 jars change only in `option`: the reset above,
and a help example that names an option every version has. This change
modifies `sable-sublevels`, which `add-sable-support` creates, so it is
archived after that change.

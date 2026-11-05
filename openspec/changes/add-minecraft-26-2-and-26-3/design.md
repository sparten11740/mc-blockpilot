# Design

## Context

See `proposal.md` for why. This design rests on a spike in a throwaway clone:
six nodes declared against the unchanged sources, compiled, and every mixin
target compared with the 26.1.2, 26.2 and 26.3 game jars.

- **Dependencies:** all six nodes resolve their loader and game jars.
- **Compile errors:** 65 on 26.2 and 128 on 26.3, the same ones on all three
  loaders. Loader entry points compile unchanged.
- **Shape of the 26.2 errors:** 49 of the 65 are `minecraft.screen`,
  `setScreen`, `getOverlay` and `options.hideGui`; the rest are the camera,
  framebuffer, section counts, particles and the GPU API.
- **Shape of the 26.3 errors:** those, plus GLFW's removal (`org.lwjgl.glfw` no
  longer exists), the GPU classes' move to `com.mojang.renderpearl.api`, and a
  changed `CommandSourceStack` constructor.
- **What the compiler can't see:** mixin targets are strings. Of 53 mixins, 3
  lost a target by name on both versions, and more changed signature (D5, D7).
  With `defaultRequire: 1`, a stale target stops the game at load.

`src/1.21.1/` and `src/26.1/` are plain source directories today: unlike
`src/main/`, they don't go through Stonecutter, so `//? if` and rename rules
have no effect in them.

## Goals / Non-Goals

**Goals:**
- All eleven targets build from the shared tree, and pass one test script with
  the same JSON shapes per loader.
- Adding the next 26.x release stays a node plus a few conditions.
- The 1.21.1 and 26.1 jars keep their behavior.

**Non-Goals:**
- Sable on these versions: no node pins `sable_companion_version`.
- Guaranteeing the Vulkan backend. Both versions default to OpenGL, and D6 uses
  only the backend-neutral API, so it is tried once and not a gate. Tried
  through MoltenVK on an M1 Max: the smoke test passes in full on 26.2 and 26.3.
- Exposing anything new from these versions, such as the graphics backend as an
  `option`, or a command for the new key names.
- A release, a `mod_version` bump, or the changelog entry that goes with one.

## Decisions

### D1. One 26.x source tree, preprocessed like the shared code

`src/26.1/` keeps serving every 26.x node (`version_sources=26.1`). The three
build scripts declare a source set named after `version_sources`, which makes
Stonecutter process it as it does `src/main/`, and add that set's directories to
`main`. `src/vanilla/` goes the same way. The extra set's own compile task is
disabled, since its classes only compile together with `main`.

The spike confirmed this on all three scripts. A `//? if >=26.2` block in
`GuiMixin` flips correctly on the 26.1 and 26.2 Fabric nodes, the five existing
nodes still build and pass their tests, and 26.2 shows the same 65 errors.

- **Merge task off:** "Set active project" writes a node's sources back to
  `src/`, and 1.21.1 nodes don't have this tree, so switching to a 26.x node and
  back left `src/26.1/` modified in Git. Disabling `stonecutterMerge<set>` for
  that set keeps the checkout clean; the spike showed the switch and a compile
  of the active node both still work.
- **Checked-in state:** the tree stays in its 26.1 form, the code for later
  versions commented out by its conditions.
- *Alternative:* a copy per version. Rejected: three copies of 47 files, of
  which about ten differ, and each fix has to land three times.
- *Alternative:* overlays, where `src/26.2/` replaces same-named files from
  `src/26.1/`. Rejected: it needs per-directory excludes or a copy task in all
  three scripts, and still duplicates whole classes for one changed line.

### D2. Exact versions, as on 26.1

Each jar names one game version, and these are what the nodes pin:
- **26.2:** Fabric API `0.161.0+26.2`, which requires loader 0.18.4; NeoForge
  `26.2.0.93`; NeoForm `26.2-2`.
- **26.3:** Fabric API `0.165.2+26.3`, which requires loader 0.19.3; NeoForge
  `26.3.0.101`; NeoForm `26.3-1`.
- **All:** Java 25, Fabric Loader 0.20.1 in development.

### D3. Moved game state: renames, and a seam for the HUD

Reads that only moved become rename rules from 26.2: `minecraft.screen` and
`minecraft().screen` to `gui.screen()`, `setScreen`, `getOverlay`,
`getMainCamera`, `getMainRenderTarget`, `getSectionRenderDispatcher`, and
`countRenderedSections`, now on `levelExtractor`. Each rule carries enough
context to be unique, as the existing rules do. A rule also runs backwards on
older versions, so its new text can't appear in shared code: `OptionalInt` to
`Optional` rewrites every real `Optional`, and takes an inline condition
instead.

`options.hideGui` can't be renamed: it is used in 15 places, some of them
assignments, and 26.2's `Hud` offers only `isHidden()` and `toggle()`. A small
class holds `hidden()` and `setHidden(boolean)` with one inline condition,
toggling only on a change, and all versions call it. The `hud` option and the
placed camera keep their behavior.

`ParticleGroup.getAll()` is gone, so `perf` reads the group's `particles` queue
through an accessor. The field exists on every 26.x version, so the accessor
needs no condition.

### D4. HUD messages and hooks

- **Messages:** action-bar, title and subtitle moved to `Hud` with the same
  signatures, so `GuiMixin` retargets by rename rules.
- **Vanilla HUD hook:** `Gui.extractRenderState` no longer takes the graphics;
  `Hud.extractRenderState(GuiGraphicsExtractor, DeltaTracker)` does, so the hook
  moves there.
- **Vanilla shutdown hook:** `Minecraft.destroy` became `exitWorldAndClose`,
  which disconnects and closes as before.

### D5. Other retargeted mixins

- **`RebuildTaskMixin`:** `RebuildTask` is now `CompileTask`, with `doTask` the
  same.
- **`GraphicsPresetMixin`:** `set` became `private static`, so its handler turns
  static by condition.
- **`GameRendererMixin` on 26.3:** `renderLevel` takes no arguments, and the
  `getBuffer` target's return type moved package. The handler is unaffected.
- **Injection points:** the targets above were checked by name and signature
  only. Whether each call still sits inside its method shows at load (D9).

### D6. Frame capture and video readback on the new GPU API

`FrameCapture` and `FrameReadback` keep their design: copy to a buffer, wait for
the copy, then map it. What changes:
- **The wait:** mapping doesn't do it, whatever 26.1's code assumed. Only OpenGL
  without `GL_ARB_buffer_storage` synchronises; a buffer mapped for good and a
  Vulkan one hand over their memory at once, so a read can get an old frame.
  This Mac's OpenGL 4.1 lacks the extension, so that path has never run here.
  Vulkan through MoltenVK showed the old frame on 26.3, and passes with the wait
  on 26.2 and 26.3. So they wait on a fence: from 26.2 that takes ending the
  submit first, between frames, as the game's own frame loop does. On 26.1 it
  takes a flush, which only NeoForge's fence does itself. A recording whose
  slots are all full waits on a fence per slot, for the oldest copy only, not
  the whole GPU queue; from 26.2 the game's own pacing means it doesn't get
  there.
- **26.2:** `buffer.map(true, false)` replaces `encoder.mapBuffer`; `GpuFormat`
  replaces `TextureFormat`, `blockSize` replaces `pixelSize`, and render passes
  take new arguments.
- **26.3:** the same code with `com.mojang.blaze3d.buffers` and `.textures`
  moved to `com.mojang.renderpearl.api`, and `GpuFormat` with them, and
  `CommandEncoder`, `RenderPass` and `GpuDevice` moved from `systems` to its
  `commands` and `device`. `bindTexture` is now `setUniform`, and `setPipeline`
  takes a compiled pipeline. All of it is done as rename rules.
- **Uniforms:** from 26.2 the blit binds the default uniforms, as the game's own
  capture does. Its pipeline declares `Globals`, and NeoForge's dev client,
  whose validation is on, crashes on the first recorded frame without them.
- **Row order:** rows stay flipped. The game's own `Screenshot` flips them the
  same way on both versions, with no branch on the backend.

### D7. Input on 26.3: SDL instead of GLFW

`org.lwjgl.glfw` is gone, so shared input code changes. Constants that exist on
every version (`InputConstants.PRESS`, `RELEASE`, `KEY_F8`) replace the GLFW
ones everywhere. The rest are conditions, because 1.21.1 lacks the `MOD_*` bits.
- **Keys:** a key's value is now an SDL scancode, and it reaches the game
  through the same `InputConstants.Key` lookups, so names keep working.
- **Key events:** `KeyEvent` is `(scancode, keycode, modifiers)`, and text-field
  shortcuts compare the keycode (`shortcutKey() == 97` for select-all). An
  injected letter gets the keycode of its place on a US keyboard, so an agent's
  `left_control+a` selects all on any layout, as it does up to 26.2, where the
  game compares the key itself. From a layout's table, AZERTY would give `q`
  there, and a non-Latin one its own letter. Other keys get theirs from
  `SDL_GetKeyFromScancode` with `key_event` on, as real events do. Only a Latin
  QWERTY layout has run here.
- **Modifiers:** the bits come from `InputConstants.MOD_*`. Left and right super
  are `KEY_LGUI` and `KEY_RGUI`.
- **Mouse buttons:** left, middle and right are now 1, 2 and 3, not 0, 2 and 1.
  `KeyNames.BUTTONS` keeps its order, which `help` and the grammar show, and a
  separate mapping turns names into the game's numbers, with an inline condition
  and a matching one in `KeyNamesTest`. Without it, `mouse click left` would
  press nothing and `mouse click right` would click as left. `buttonName` maps
  the other way, so an interrupt names a real click's button: unchanged, a real
  left click would say `right`.
- **Mouse movement:** `onMove` gained relative `xrel` and `yrel`, which is what
  it uses while the mouse is grabbed. `blockpilot$move` computes them from the
  shadowed `xpos` and `ypos`, or the camera wouldn't turn.
- **Cursor:** a new screen or a resize now rereads the OS pointer, which would
  forget where an agent put the cursor, so a click that opened a screen left it
  elsewhere than on 26.2. While the agent moved the mouse last, the reread is
  skipped; a real move ends that.
- **Pointer:** `InputConstants` warps the pointer as the mouse is grabbed or let
  go. GLFW ignored that for a window without focus, but SDL moves the real
  pointer anyway, so an agent's `key escape` in a background window would move
  the player's. The warp is skipped unless the window really has focus.
- **Polling:** `InputConstants.isKeyDown(int)` drops the window, so the handler
  signature changes by condition.
- **Focus:** `Focus.reallyFocused` asks SDL for the window's input focus on
  26.3. `Window.isFocused()` starts true and only changes on SDL's focus events,
  so a window that never had focus would read as focused.
- **Typing:** `charTyped` remains, and the game's own `textInput` calls it per
  code point, so `type` is unchanged.
- **Take-over:** real events now arrive through `minecraft.execute`, on the same
  client thread, and `Injection.active()` still tells them from injected ones.
  SDL's relative mode keeps a grabbed mouse inside the window, so at an edge its
  place stops changing. While the mouse is grabbed the take-over reads its
  relative movement, as the game does, which is what an interrupt's `dx` and
  `dy` then report, as a grabbed mouse has no cursor to measure.

*Alternative:* a per-version class hiding all input constants. Rejected: only
26.3 differs, and in a few lines each.

### D8. The vanilla agent's version comes from its manifest

`BlockpilotAgent` hardcodes `26.1.2`, and its source set is compiled for Java 8,
outside Stonecutter. The build writes a `Blockpilot-Minecraft` attribute from
the node's `minecraft_version`, and the agent reads it from its own jar.

*Alternative:* a chiselled constant. Rejected: it would bring the agent's
separate source set into Stonecutter for one string.

`AccessWidening` and `accesstransformer.cfg` stay. `OptionInstance$ValueSet` is
already public on 26.2 and 26.3, and widening a public class does nothing.

### D9. Checking the port

- **Compile and unit tests:** all eleven nodes, in `./gradlew build`.
- **Load:** Mixin applies as a target class loads, and `defaultRequire: 1` makes
  a missing injection point stop the game then. Reaching a screen shows only the
  mixins loaded by then, so D5's check is the world run, whose failures may name
  an unrelated step.
- **Everything else that can be asserted:** the script in D10, on every target,
  so the 1.21.1 and 26.1 regression run is the same run as the port's.
- **By hand:** what D10 lists as out of a script's reach. Whether the CurseForge
  app offers these versions waits for the first release, as its uploads do.
- **Clients:** dev clients are closed with `quit`. One that never answered, or
  doesn't close, is stopped through the build that runs it, and by process id if
  its endpoint names one. Only Blockpilot's own dev client is ever stopped.

### D10. One script for every target

`tools/smoke_test.py` stays the test of one running game, and gains assertions.
A new `tools/test_targets.py` runs it on each target: it starts the dev client,
waits for the endpoint, runs the smoke test in text and JSON mode, quits the
client and moves on. It finishes with a per-target table, compares JSON shapes
across targets of a loader, where the vanilla targets count as Fabric, whose
replies they share, and exits non-zero on any failure.

- **Target list:** read from `settings.gradle.kts`, so a new node is tested
  without editing the script. `--targets` narrows it.
- **Jar checks with no game:** each jar exists under its expected name and lacks
  the other loaders' classes, loader metadata pins its version, a vanilla
  manifest names its version, and 26.3 jars don't reference `org.lwjgl.glfw`.
- **Wrong-version agent:** the agent runs against a directory holding only a
  `version.json` naming another version, under `java -version`. That needs no
  game, and it asserts the refusal and its log line.
- **New assertions, each on what the game then does:**
    - `left`, `right` and `middle` in a world break, place and pick a block.
    - Buttons in a screen: left activates a widget, and right and shift clicks
      act on a stack, at the slot's place on screen. `click s<i>` wouldn't do:
      it drives the container directly, past the mouse and its modifiers.
    - `mouse move` changes `status`'s yaw in a world and moves the cursor in a
      screen, by the distance asked. A click that opens another screen leaves
      the cursor where it was.
    - `type` fills the chat box, then select-all and typing replaces it. The
      chord is `left_win+a` on macOS and `left_control+a` elsewhere, as the
      game's own `EDIT_SHORTCUT_KEY_MODIFIER` requires.
    - Shift-click moves a stack, which needs the modifier bits.
    - Control held across ticks makes the drop key throw the whole stack, which
      reaches `isKeyDown` through the game's `hasControlDown` (Command on macOS
      before 26.1). A key held across a closing screen wouldn't do: macOS skips
      that polling.
    - `key press f8` is refused as the toggle, which proves the toggle resolved
      to F8 under each version's key numbers.
    - `scroll` changes the selected slot.
    - `pixels` reads sky above ground, `@<pos>` on a coloured block reads that
      colour, and an ffmpeg-decoded frame of a `record` is upright too.
    - `perf` reports a section rebuild, a block entity's time and live
      particles.
    - `hud` and `graphicsPreset` (or `graphicsMode`) set and reset, by version.
- **Honest failures:** each new assertion is made to fail once by breaking what
  it checks, so none passes vacuously. A prerequisite missing on the machine
  (ffmpeg) is a skip with its reason, never a pass.
- **Out of a script's reach, so by hand:**
    - Real input taking control back, and a real F8 press: `Injection.active()`
      separates injected from real input by design, and agents are refused F8.
      Faking them would need OS-level events, and a test hook in the shipped
      jar.
    - The Minecraft Launcher and CurseForge app install paths: they are GUI apps
      behind a Microsoft login.
    - Whether a video or screenshot looks right beyond orientation and colour.
    - Windows and Linux, until someone runs the script there.

*Alternative:* a separate assertion suite beside the smoke test. Rejected: the
two would drift, and most steps would be copies.

*Alternative:* running targets in parallel. Rejected: clients compete for the
GPU and window, which makes timing-sensitive assertions flaky.

## Risks / Trade-offs

- [The spike only sees compile errors and mixin signatures, so behavior can
  change with no error] → D10's assertions check effects rather than replies,
  and the JSON shapes catch reply changes.
- [A full run takes about 25 minutes, and a person using the game window
  interrupts it, because real input takes control] → `--targets` narrows a run,
  and the docs say so. Any failure names its target and assertion, so a stray
  one is rerun alone.
- [Rename rules can rewrite shared text by accident, and `minecraft.screen` is
  short] → All nodes are rebuilt after each rule. The spike's sources show only
  the field uses `minecraft.screen`.
- [Eleven nodes make builds and CI slower] → Gradle already caches and runs
  nodes in parallel. Accepted as the cost of the targets.
- [CurseForge may not list 26.2 or 26.3 as game versions yet, which only shows
  at upload] → The workflow runs on a tag, so this surfaces at the next release,
  and `fail-fast: false` keeps one bad row from stopping the others. It can't be
  tested beforehand.
- [SDL input is tested only on the development Mac] → The key tables and event
  flow are the game's own and shared across platforms. Windows and Linux are
  covered by what players report, as on other targets.
- [The vanilla agent bundles Mixin for a Java 25 game that keeps changing] → The
  load check runs on both `-vanilla` nodes.
- [Each new 26.x adds conditions to one tree] → Accepted; a condition older than
  the oldest supported 26.x can be dropped when that target goes.

## Migration Plan

Nothing to migrate. Developers building from source need nothing new: Gradle
fetches Java 25 as it already does. `src/26.1/` keeps its name, and
`docs/building.md` says it serves 26.1 and later. To roll back, revert the
change; no stored data or protocol is touched.

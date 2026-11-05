# Proposal

## Why

Blockpilot stops at Minecraft 26.1.2, two releases behind the current 26.3, so
mods developed for 26.2 or 26.3 can't be play-tested with it, and players on
those versions can't use it. Most new mod builds and players have moved on:
26.2 and 26.3 are where the release line is now.

## What Changes

- Six new targets, for the same three loaders 26.1 has: `26.2-neoforge`,
  `26.2-fabric`, `26.2-vanilla`, `26.3-neoforge`, `26.3-fabric` and
  `26.3-vanilla`, for Minecraft 26.2 and 26.3 on Java 25. The existing targets
  stay.
- Every command answers the same on 26.2 and 26.3 as on 26.1. The game's own
  changes stay out of the agent's view: 26.2 splits the HUD off `Gui` and
  reworks the GPU buffer API, and 26.3 replaces GLFW with SDL, which renumbers
  keys and mouse buttons, and moves the renderer to new packages. Agents keep
  the same key and button names.
- The one exception is what 26.3's key table changes: `f25` is gone, and 127
  key names are new. Both follow from key names being the running game's.
- The sources for 26.1 and later stay one tree, `src/26.1/`, with inline version
  conditions, instead of a copy per version. That tree and `src/vanilla/` now go
  through the same preprocessing as the shared code.
- The vanilla agent checks the game version its own jar was built for, rather
  than a fixed 26.1.2.
- A script tests every target end to end: it starts each client, runs assertions
  against it, closes it, and exits non-zero naming each failing target and
  assertion. It extends the smoke test with what it doesn't reach today, such as
  what each mouse button does, the camera, text shortcuts and upright frames.
- The release workflow builds eleven jars and uploads eight to CurseForge. The
  README and docs cover the new targets.

Out of scope: Sable integration on these versions (no node pins a companion, so
the jars say so in `help sable`, as on 26.1), guaranteeing the experimental
Vulkan backend, and cutting a release.

## Capabilities

### New Capabilities

None.

### Modified Capabilities
- `multi-target-build`: the targets now include the 26.2 and 26.3 ones; agents
  use the same key and button names on every target; the vanilla jar's manifest
  names its game version; one script tests every target, including what input
  and capture do in the game.
- `vanilla-agent`: an agent for each of 26.1.2, 26.2 and 26.3, each running only
  on its own version.
- `video-recording`: a GPU that doesn't finish reading a frame back ends a
  recording rather than the game.
- `extension-api`: the one API jar works with the 26.2 and 26.3 jars too.

## Impact

- **Build:** six new nodes with their properties; the per-version trees go
  through Stonecutter in all three build scripts; new rename rules.
- **Code:** inline conditions and rename rules in the shared code that reads the
  screen, HUD, camera and framebuffer; the input classes stop using GLFW on
  26.3; frame capture and video readback move to the new GPU API; a handful of
  mixins retarget (`GuiMixin`, `RebuildTaskMixin`, `GameRendererMixin`,
  `GraphicsPresetMixin`, the input mixins and the vanilla HUD and shutdown
  hooks).
- **Dependencies:** Fabric API, NeoForge and NeoForm builds for 26.2 and 26.3.
- **Tests:** `tools/smoke_test.py` gains assertions, and a new script runs it on
  every target.
- **Release:** `.github/workflows/release.yml` expects eleven jars, and its
  CurseForge matrix gains four rows.
- **Docs:** `README.md`, `docs/building.md`, `docs/connecting.md`,
  `docs/dev-environment.md`, `docs/sable.md` and `docs/commands.md`.
- **Existing jars:** the 1.21.1 and 26.1 jars keep their behavior, though the
  shared input code they compile from changes, so they are smoke-tested again.
  26.1's capture changes too: it now waits for the GPU's copy, which is a fix on
  machines with OpenGL 4.4 or newer.

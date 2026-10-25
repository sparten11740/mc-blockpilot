# Tasks

## 1. Build (multi-target-build)

- [ ] 1.1 Declare the `26.1-neoforge` and `26.1-fabric` nodes with their properties (D1), copy `src/1.21.1/` to `src/26.1/` with an access widener in the official namespace, and compile the Sable compat package only on nodes with a non-empty `sable_companion_version`; verify both 1.21.1 nodes still pass `check` and both 26.1 nodes configure
- [ ] 1.2 Add rename rules for the one-for-one renames (D2); verify both 1.21.1 nodes still pass `check` and their generated sources are unchanged

## 2. Shared code (all capabilities)

- [ ] 2.1 Port the shared game code with `//? if >=26.1` blocks (inventory, container clicks, permissions, game rules, session, teleport, build height, entity saving, input calls, HUD drawing) until only the version-specific pieces of 2.2 to 2.5 are missing; verify both 1.21.1 nodes pass `check`
- [ ] 2.2 Add the per-version recipe lookup (D3) for 1.21.1 and 26.1 and move `craft` onto it, with `help craft` naming the remote-server gap on 26.1; verify on 1.21.1 that the smoke test's crafting steps still pass, on 26.1 singleplayer that the smoke test's crafting steps pass and locked recipes craft in the grid and at a table, and on a local vanilla 26.1.2 server (offline mode) that a locked recipe gives `err no_recipe <item>` and an unlocked one crafts
- [ ] 2.3 Port screen capture to a per-version frame reader (D4) for `screenshot`, sheets, the pixel commands and the MCP tool; verify on 26.1 that a screenshot, a burst sheet, `pixels` and the MCP screenshot tool all return images of the current frame
- [ ] 2.4 Turn the Sable integration off with "this Blockpilot build has no Sable integration" when Sable is installed but the jar has no compat package (D5); verify on the 1.21.1 NeoForge dev client built with `-Psable_companion_version=` and Sable in its mods folder that the log and `help sable` say so and `status` works, and that the normal build still turns the integration on
- [ ] 2.5 Port the client options to 26.1's names (D6): `graphicsPreset`, `cutoutLeaves` and `improvedTransparency` instead of `graphicsMode`, originals recorded for every option that changes, a hook on the preset's setter for unlisted ones, the preset restored first; verify on 26.1 that `option graphicsPreset fast` changes the render distance and leaves, `option reset` brings back every value, `option graphicsMode fast` is `err syntax`, and on 1.21.1 that `option graphicsMode fast; option reset` still works

## 3. Mixins and loaders (multi-target-build)

- [ ] 3.1 Port the mixins in `src/26.1/` to their 26.1 targets, with the render timings of D7; verify `26.1-neoforge` passes `check`, its dev client starts with no mixin warnings or errors in the log, and a capture of a scene with a chest, entities and flame particles lists the chest renderer once per frame, the entity renderers, the particles and the render stages by their 26.1 names
- [ ] 3.2 Port the Fabric half (Fabric API renames, entry points, events) to 26.1; verify `26.1-fabric` passes `check`, its dev client starts with no mixin warnings or errors in the log, and the same scene capture lists the same renderers and particles

## 4. Integration checks

- [ ] 4.1 Run `tools/smoke_test.py` in text and JSON mode on the 26.1 NeoForge and Fabric dev clients; verify every step passes and the JSON shapes match the 1.21.1 jar of the same loader
- [ ] 4.2 Build all four jars; verify their names, that the Fabric jars hold no NeoForge classes and no jar holds `dev/ryanhcode` classes, that each metadata file names its own Minecraft version range, and that the `dev.blockpilot.api` classes in the 26.1 jars have the same public signatures as the `blockpilot-api` jar
- [ ] 4.3 Update the README (targets, dev-environment snippets for 26.1, the container and `craft` commands with the crafting gap, the 26.1 option names, Sable on 26.1, the build layout and the add-a-target notes); verify every command, option and version it names matches the build

## Workflow follow-up

- Archive after `add-sable-support`, whose `sable-sublevels` capability this
  change adds to.

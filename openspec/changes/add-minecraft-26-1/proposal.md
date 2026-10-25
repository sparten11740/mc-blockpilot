# Proposal

## Why

Blockpilot builds for Minecraft 1.21.1 only, so mods developed for the current
release (26.1) can't be play-tested with it. The build was set up for more
targets from the start; 26.1 is the first one to add.

## What Changes

- Two new targets, `26.1-neoforge` and `26.1-fabric`, for Minecraft 26.1.2 on
  Java 25, built from the same source tree. 26.1 is the first unobfuscated
  release: its jars need no remapping, and Fabric uses plain Loom.
- The game code that differs from 1.21.1 is ported with rename rules and
  inline version conditions, and the mixins get their own 26.1 copy. Every
  command answers the same on 26.1 as on 1.21.1, with these differences: on
  a remote server, `craft` finds only the recipes the player has unlocked; the
  graphics options follow 26.1's, where Fast, Fancy and Fabulous became
  presets; and performance captures time renderers and particles where 26.1
  runs their code, in its render-state steps.
- No Sable exists for 26.1 yet, so the 26.1 jars are built without the Sable
  integration. With Sable installed later, they run as without Sable and say
  why in `help sable`.
- The README's command reference, dev-environment instructions and target
  list cover 26.1.

## Capabilities

### New Capabilities

None.

### Modified Capabilities
- `multi-target-build`: the targets now include `26.1-neoforge` and
  `26.1-fabric`.
- `extension-api`: the one API jar works with the 26.1 jars too.
- `player-control`: on 26.1, `craft` on a remote server finds only unlocked
  recipes, because the client no longer receives the others.
- `client-observation`: option names follow the running version, so 26.1 has
  `graphicsPreset`, `cutoutLeaves` and `improvedTransparency` instead of
  `graphicsMode`; `option reset` also restores options the game changed along
  with the one an agent set.
- `performance-capture`: from 26.1, renderer and particle times cover the
  render-state steps their code runs in, and NeoForge's render stages keep
  the names NeoForge gives them.
- `sable-sublevels` (from `add-sable-support`): a target built without the
  Sable integration runs as without Sable and says so.

## Impact

- **Build:** the two 26.1 nodes and their properties; the Sable companion is
  compiled against only on nodes that have one.
- **Code:** a `src/26.1/` copy of the mixins and access widener, and
  per-version classes for recipe lookup and frame capture; `//? if` blocks and
  rename rules for the changed game API (input callbacks, container
  clicks, recipe placement, screenshots, permissions, game rules, options,
  entity saving, the renamed `Identifier` and `GuiGraphicsExtractor`).
- **Protocol:** none; replies keep their form on every target.
- **Docs:** README targets, dev-environment snippets and add-a-target notes.
- **Testing:** unit tests and the smoke test on all four jars; both 26.1 dev
  clients started and driven.

# Proposal

## Why

Blockpilot only runs inside a mod loader. Players and agents who want it on a
plain Minecraft install, with no NeoForge or Fabric, have to set a loader up
first, and agents playing the game rather than testing a mod gain nothing from
one. Minecraft 26.1 ships unobfuscated, so the classes Blockpilot's mixins
target have the same names in the vanilla client as in development, which
makes a loader-free build practical there.

## What Changes

- A new target, `26.1-vanilla`: a jar that runs as a Java agent on the vanilla
  26.1.2 client. It is installed by adding `-javaagent:<path to the jar>` to the
  JVM arguments of a launcher profile.
- The agent bootstraps Mixin itself and applies Blockpilot's mixins and its
  access widener to the game's classes as they load. Mixin, MixinExtras, ASM
  and the access-widener library are bundled in the jar, since vanilla ships
  none of them.
- A vanilla platform layer provides what the loaders provide today: the game
  and config folders, the key mapping, the translations, and the events
  (client ticks, the HUD, chat, screens, the integrated server's start, ticks,
  chunks and stop), through mixins of its own.
- What needs a loader stays unavailable and says so: `config` answers
  `err unsupported` as on Fabric, and no other mod can add commands.
- The jar is built, tested and attached to GitHub releases with the others.
  CurseForge, the one store the release workflow uploads to, has no loader
  for agents, so it isn't uploaded there.
- The README and docs explain installing it.

## Capabilities

### New Capabilities
- `vanilla-agent`: running Blockpilot on the vanilla client as a Java agent,
  without a mod loader.

### Modified Capabilities
- `multi-target-build`: the `26.1-vanilla` target, and its metadata being a
  Java agent manifest rather than loader metadata.

## Impact

- `settings.gradle.kts`, a new `build.vanilla.gradle.kts` on ModDevGradle's
  vanilla mode (NeoForm 26.1.2), and a `26.1-vanilla` node.
- A new `dev.blockpilot.platform.vanilla` package, in the vanilla jar only,
  with the agent, its Mixin service and its mixins; Stonecutter conditions that
  assume a loader learn about `vanilla`.
- The release workflow attaches the vanilla jar to GitHub releases.
- README and `docs/connecting.md`.

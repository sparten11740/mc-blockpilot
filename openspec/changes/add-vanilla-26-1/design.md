# Design

## Context

- Minecraft 26.1.2 ships unobfuscated: `net.minecraft.client.main.Main`
  starts it on Java 25, with Mojang's names at run time. Its 107 libraries
  include neither ASM nor Mixin.
- The vanilla launcher lets a profile add JVM arguments, so
  `-javaagent:<jar>` reaches the client unchanged.
- The loaders give Blockpilot three things: mixin application, an access
  widener (one entry on 26.1, `OptionInstance$ValueSet` made accessible),
  and the `ClientPlatform` seam, whose `Services` (game and config folders,
  mod lookups, key mappings, mod configs, creative tabs) and `Hooks` (load
  complete, client tick end, HUD, chat, screen init, client stopping, server
  started, tick end, chunk loaded, stopping) each loader implements from its
  events.
- Shared code switches on the loader in 13 files with `//? if neoforge` or
  `//? if fabric`. Most separate NeoForge's patched API from the plain one,
  which Fabric uses and vanilla has too.
- ModDevGradle has a vanilla mode, which compiles against Minecraft decompiled
  by NeoForm (`26.1.2-1`) and runs the plain client; it is the same plugin the
  NeoForge nodes use.

## Goals / Non-Goals

**Goals:**
- One jar that turns a vanilla 26.1.2 client into a Blockpilot client through
  the launcher's JVM arguments.
- The shared code and the 26.1 mixins unchanged in what they do.

**Non-Goals:**
- Vanilla 1.21.1 or 1.20.1: obfuscated at run time, so the mixins would need
  remapping to the obfuscated names. A later change can add that.
- Other mods on a vanilla client: there is nothing to load them.
- A dedicated server.

## Decisions

### D1: A Java agent

`Premain-Class` in the vanilla jar's manifest names the agent. In `premain`
it checks the game version (from the client jar's `version.json`), boots
Mixin, adds a `ClassFileTransformer`, and leaves the rest to the mixins,
which start Blockpilot from the game's own startup.

The entry class is compiled for Java 8 and needs nothing but the JDK. A
client of another version can run on an older Java than 26.1's 25, where a
class built for Java 25 wouldn't load and the JVM would stop before the game
starts. The entry class loads anywhere, turns another version away with a
line in the log, and starts the Java 25 bootstrap only on 26.1.2.

Alternatives:
- Editing the client jar or the version JSON: changes the installation, and
  breaks with every launcher update.
- A wrapper main class in a custom launcher profile: needs a new version JSON
  per Minecraft version, and players editing launcher files.

### D2: Mixin with a service of the agent's own

Mixin runs in an environment through an `IMixinService`. The agent provides
one:
- class bytes come from the system class loader's resources, since the game,
  its libraries and the agent are all on the class path;
- transformation is the agent's `ClassFileTransformer` handing each class to
  Mixin's transformer, and to the access widener (D4);
- the phase goes to `DEFAULT` once the game's main class is about to load.

Fabric's Mixin build (`net.fabricmc:sponge-mixin`), MixinExtras, ASM and the
access-widener library are bundled into the jar, unrelocated, as no other
copy is on a vanilla class path. Mixin reads ASM's version from the manifest
entry of ASM's package, and refuses Java 21 mixin configs when it finds
none, so the jar's manifest carries that entry. The jar also carries each
bundled library's license, adding those of ASM and the access-widener
library, whose jars have none. The configs are the shared
`blockpilot.mixins.json` and a `blockpilot-vanilla.mixins.json` for the
platform's own mixins.

Alternative: Fabric Loader itself, launched as a library. It would bring its
mod discovery and its own launcher, which is what this target goes without.

### D3: A vanilla platform layer from mixins

`dev.blockpilot.platform.vanilla`, only in the vanilla jar:
- `Services`: the game folder from `Minecraft.gameDirectory`, config under it,
  the mod version from the jar's manifest, no other mods, no mod configs (as
  Fabric, `config` is `err unsupported`), and the creative tabs of the one
  page vanilla has.
- `Hooks`, each from a mixin at the point Fabric API's event fires: the end of
  `Minecraft` startup, `tick`, the HUD's render, chat and game messages
  received, `Screen.init`, `Minecraft.stop`, and the integrated server's
  start, tick end, chunk load and stop.
- The key mapping goes into `Options.keyMappings` as `Options` is built, as
  Fabric API's helper does; its category into the categories it shows.
- The translations come from the jar's `en_us.json`, added to the client
  language as it loads, since vanilla loads resources only from packs.

### D4: The access widener applied by the agent

The agent reads the same `blockpilot.accesswidener` with the access-widener
library and applies it in its transformer, so the one file serves Fabric and
vanilla on the client. Compiling against vanilla goes through ModDevGradle,
which takes access transformers rather than wideners, so the node also has a
one-line `accesstransformer.cfg` opening the same class.

### D5: A `26.1-vanilla` node

`target("26.1", ..., "vanilla")` in `settings.gradle.kts` and a
`build.vanilla.gradle.kts` on ModDevGradle's vanilla mode:
- it excludes the NeoForge and Fabric platform packages, as they exclude each
  other's; its own platform package is in `src/vanilla`, outside the tree
  Stonecutter rewrites, so it is written against 26.1 names directly;
- the Stonecutter loader constant learns `vanilla`; conditions whose
  non-NeoForge branch uses Fabric API get a `vanilla` branch;
- the jar bundles D2's libraries and gets the agent manifest instead of loader
  metadata;
- the dev client runs with `-javaagent` pointing at the freshly built jar.

### D6: Released on GitHub and Modrinth

The release workflow attaches the vanilla jar to the GitHub release and
uploads it to Modrinth, which lists mods by loader and has a `java-agent` one
for jars like this. CurseForge has no such loader, so it isn't uploaded there.

### D7: A spike first

Booting Mixin from an agent is the one step no other target has proven. The
first task does only that against the vanilla dev client, with one mixin. If
it can't be made to work, the change stops there and is re-planned.

## Risks / Trade-offs

- [Mixin's service API changes between releases] → The version is pinned, and
  the service covers only what an agent needs.
- [Classes load before Mixin is ready] → `premain` runs before the game's
  main class, and the agent loads none of the game's classes itself.
- [A player adds the agent to a modded profile] → It applies its mixins next
  to the loader's; the README says to use it on vanilla only.

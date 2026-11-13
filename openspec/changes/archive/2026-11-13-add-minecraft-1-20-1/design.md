# Design

## Context

See `proposal.md` for why. This design rests on a spike in this change's
worktree: a `1.20.1-fabric` node declared against the unchanged sources and
compiled.

- **Dependencies:** Fabric API `0.92.x+1.20.1`, Parchment `1.20.1` /
  `2023.09.03` and the remapping Loom resolve; the node compiles with Java 17.
- **Java level:** the first compile stops at Java 21 syntax: switch patterns
  over `Statement`, `Form.Element` and config values, `case null` and `when`
  guards. A few Java 21 calls follow (`List.getFirst`, `List.reversed`,
  `HttpClient.shutdown`, `Math.ceilDiv`).
- **API errors:** 94 with that fixed. Most are renames or older signatures:
  reach (`blockInteractionRange`), item components and enchantments, world
  loading (`WorldOpenFlows`), `GenericMessageScreen`, `ServerData.Type`,
  `BlockEntity.saveWithFullMetadata`, packet types in `perf`, chunk sending and
  section rendering in `Settle`, `DeltaTracker`, and the GameTest runner.
- **The one missing feature:** `ServerTickRateManager`. 1.20.1 has no tick
  rate manager, as it came with `/tick` in 1.20.3.
- **What the compiler can't see:** mixin targets are strings, so those are
  checked at load (`defaultRequire: 1` stops the game on a stale one).

## Goals / Non-Goals

**Goals:**
- `1.20.1-fabric`, then `1.20.1-forge`, pass the smoke test with the JSON
  shapes of 1.21.1 on Fabric and NeoForge.
- The eleven existing jars keep their behaviour.
- Glowline's play tests run on 1.20.1: `block`, `place`, `use`, `camera`,
  `screenshot`, `pixels`, `tick freeze` and `perf`.

**Non-Goals:**
- A 1.20.1 vanilla agent, Sable on 1.20.1, Forge after 1.20.1.
- Baritone in 1.20.1 dev runs: neither 1.20.1 node names a Baritone build, so
  `--baritone` skips them.
- A release or version bump.

## Decisions

### D1. Shared code keeps to Java 17, by hand

The 1.20.1 launcher profiles run Java 17, and a class file built for Java 21
doesn't load there. So the shared code is rewritten where it uses Java 21:
switch patterns become `instanceof` chains, and Java 21 calls get Java 17
ones. The 1.20.1 node compiles `main` with Java 17, so a Java 21 construct
creeping back in fails that build. The API jar compiles with `--release 17`,
so a 1.20.1 extension can compile against it.

- **Tests stay on Java 21:** the 1.20.1 node compiles and runs its unit tests
  with a Java 21 toolchain. They don't ship, and the grammar tests lean on
  record patterns.
- *Alternative:* a bytecode downgrader (e.g. jvmdowngrader) after compiling
  with Java 21. Rejected: another build tool between the compiler and the
  remapper and mixin, stub classes shipped in the jar, and stack traces that
  no longer match the source, for about 70 small rewrites.

### D2. One tree for 1.20.1 and 1.21.1

The 1.20.1 nodes build from `src/1.21.1/` (`version_sources=1.21.1`), with
inline `//? if` conditions where 1.20.1 differs, as `src/26.1/` serves every
26.x release. Shared code takes conditions and rename rules too.

- **Backward renames:** rename rules so far apply from a version on; 1.20.1's
  older names get `older(...)` rules that apply before 1.21, with the same rule
  that a new text must not appear in shared code.
- **One boundary:** no release between 1.20.1 and 1.21.1 is built, so every
  1.20.1 difference is `//? if >=1.21` (or `<1.21`), rather than the release
  that changed each API.
- *Alternative:* a copy as `src/1.20.1/`. Rejected: most of the tree's 17
  files differ by a line or two, and a fix would have to land twice.

### D3. Tick control ported for 1.20.1

`ServerCommands` reaches ticking through a small facade, `ServerTicking`,
with inline conditions: from 1.21 on it calls the server's
`tickRateManager()`, and on 1.20.1 Blockpilot's own port, whose mixins are in
the 1.21.1 tree. The port behaves like the manager it stands in for:

- **Frozen:** the level skips weather, game time, block and fluid ticks, block
  entities, raids and block events, and ticks only players and what they ride;
  chunks still load. Mixins on `ServerLevel` and `Level` check the port's
  state.
- **Step:** while frozen, `tick step <n>` lets `n` whole ticks run normally.
- **Rate:** 1.20.1's server loop adds 50 ms per tick; a mixin replaces it with
  `1000 / rate` ms, carrying the remainder over so that the average holds for
  rates that aren't a whole number of milliseconds. Above 1000 ticks a second
  some ticks get 0 ms, so the server runs as fast as it can, as 1.20.3 does.
- **Client:** 1.20.3's client ticks at its integrated server's rate, so slow
  motion slows it too; on 1.20.1 a mixin sets the client timer from the port.
- **Leave:** undone on server stop like the manager's settings today.
- The injectors sit in mixin classes both versions load, behind `//? if
  <1.21`, and empty on later versions, so the mixin config stays one file.

### D4. Version-specific readings

- **Reach:** a helper gives the block and entity reach: from 1.20.5 the
  player's attributes, on 1.20.1 the game's own numbers (`getPickRange()` for
  blocks; 3 blocks for entities, 6 in creative, as 1.20.1's `pick` does).
- **Items:** 1.20.1 reads enchantments and custom names from the stack's tag.
- **Packet names:** 1.20.1 packets have no type id, so `perf` derives the name
  from the class (`ClientboundSetEntityMotionPacket` becomes
  `set_entity_motion`), which matches the ids 1.20.5 gave most of them.
- **GameTests:** 1.20.1's runner takes test functions and a corner directly;
  its listener has three callbacks, so the adapter in the tree fills the rest.
- **Packet ids:** a table generated from 1.20.1's `ConnectionProtocol`, built
  from class literals: Fabric's production game runs with intermediary class
  names, so names can't be read off the classes at run time.
- **New chunks:** Fabric API 0.92 has no generate event, so a mixin on the
  constructor that makes a full chunk from a proto chunk reports them, for
  both 1.20.1 loaders.
- **Camera:** 1.20.1 turns the view by yaw and pitch, not the camera's
  rotation, so a placed camera's roll goes into the pose, and the frame's
  camera is recorded after the level renders, as the HUD that does it later
  isn't drawn while hidden.

### D5. Forge as a fourth loader

- **Build:** `build.forge.gradle.kts` with ModDevGradle's `legacyforge` plugin
  (2.0.148, as for NeoForge), compiled and run against Forge 47.1.3, the one
  NeoForge 47.1 split from, so the jar uses nothing either lacks. The
  production jar is `reobfJar`'s output, sent to the root `build/libs`. A Mixin
  annotation processor writes the refmap Forge needs at run time, and the
  manifest lists the mixin configs, where Forge 1.20.1 looks for them.
- **Refmap:** a mixin with two targets can't map a method both name, as their
  intermediate names differ, so the entity tick timing is a mixin per level.
- **MixinExtras:** Forge 47 doesn't bundle it, so the jar nests it, and Forge
  loads the newest copy any mod brings.
- **Access:** Forge reads an access transformer, kept beside the access
  widener, for the type the widener opens on Fabric.
- **Metadata:** `META-INF/mods.toml` from a template, `pack_format` 15,
  `loaderVersion` `[47,)` and the `forge` dependency `[47.1,)`.
- **NeoForge 47.1:** it loads Forge 1.20.1 mods, but not Forge 47.3's
  constructor injection, so the mod's constructor takes no arguments and uses
  `FMLJavaModLoadingContext.get()`. Tried once on NeoForge 47.1.106, not a
  gate: `-Pblockpilot.neoforge=47.1.106` runs the node on it.
- **Code:** `dev.blockpilot.platform.forge`, a port of the NeoForge entry point
  and services to Forge's events, and `//? if forge` where shared code names a
  loader. Every other build excludes the package.
- **Replies:** Forge's replies are compared with NeoForge's in the test
  script.

## Risks / Trade-offs

- **Mixins the compiler can't check** → each 1.20.1 node runs the full smoke
  test before it counts as done.
- **A port of tick control can drift from 1.20.3's** → its scenarios run in
  the smoke test on 1.20.1 like on every other target.
- **Rewriting shared code touches every target** → the existing nodes compile,
  pass their unit tests and one smoke run on 1.21.1.
- **Two older toolchains** (Java 17, legacyforge) slow the full build → only
  the two 1.20.1 nodes use them.

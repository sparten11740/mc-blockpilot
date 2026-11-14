# Design

## Context

On 1.20.1 and 1.21.1, `LevelRendererMixin` redirects the player's position
in `LevelRenderer.setupRender` to the camera's while a camera is placed
`away`, as those versions centre the sections they draw on the player. Sodium
(1.20.1 Fabric and later) and Embeddium (1.20.1 Forge) overwrite
`setupRender` with their own, which calls `SodiumWorldRenderer.setupTerrain`
with the camera and never reads the player's position. Mixin refuses to
inject into a method another mixin overwrote unless the injecting mixin's
priority is higher: `InjectionPoint.checkPriority` passes only when the
merging mixin's priority is lower than the injector's. Both renderers use the
default, 1000.

Reading the jars' class files shows `setupRender` as the only method
Blockpilot hooks that Sodium 0.5, 0.8 and 0.9 or Embeddium 0.3 overwrite, on
every version they exist for; 1.21.11 and 26.x have no `setupRender` hook.

Section rebuilds are counted at the head of the game's rebuild task. Sodium
meshes sections in `ChunkBuilderMeshingTask.execute`, in
`net.caffeinemc.mods.sodium` from 0.6 on and in `me.jellysquid.mods.sodium`
in Sodium 0.5 and Embeddium.

## Goals / Non-Goals

**Goals:**
- No code that names or detects a renderer for loading: a priority and
  optional redirects work beside any mod that overwrites `setupRender`.
- Sodium's drawing untouched.

**Non-Goals:**
- Compiling against Sodium, or an API dependency on it.

## Decisions

### D1: A priority above the default, and optional redirects

`LevelRendererMixin` gets priority 1100, and its three redirects `require =
0`. Beside a renderer that overwrote `setupRender`, they find no call to
redirect, as its `setupRender` reads no player position, and the mixin
applies without them; the renderer centres on the camera itself. Without
one, they redirect as before. The `renderLevel` injection, which records the
frame's camera, applies either way.

Alternative: a mixin plugin that skips the redirects when Sodium is loaded.
It would name each renderer and miss the next one.

### D2: Count rebuilds in the renderers' meshing tasks

Two `@Pseudo` mixins with `remap = false` inject at the head of
`ChunkBuilderMeshingTask.execute`, one per package, and call
`Perf.sectionRebuilt()` as the game's task does. Each names the method by its
full descriptor, since the class also has a bridge method of that name that
calls it, which would count each rebuild twice. A missing class skips the
mixin; `require = 0` makes a changed method lose only the count rather than
stop the game.

### D3: Testing beside the renderers

Each renderer's jar goes into a dev client's `run/mods`, except Embeddium:
the 1.20.1 Forge dev run uses the development names, so Embeddium comes in
as a `modRuntimeOnly` dependency from Modrinth's Maven, which the build
remaps. Sodium 0.5 refuses the LWJGL 3.3.2 of the 1.20.1 Fabric dev run, so
that run passes `-Dsodium.checks.issue2561=false`. Both build lines are for
the check only and aren't committed.

## Risks / Trade-offs

- [A later Sodium renames its meshing task] → rebuilds read 0 beside it
  until a mixin names the new one; nothing breaks.
- [A renderer whose `setupRender` still reads the player's position] → the
  redirects would then apply to it, centring on an away camera, which is
  what they are for.

# Design

## Context

- `ClientPlatform.Services` knows a mod's version by id and Blockpilot's own
  paths, but has no list of every loaded mod. Each loader has one: NeoForge
  and Forge `ModList.getMods()` with each mod's owning file, Fabric
  `FabricLoader.getAllMods()` with each container's origin (its paths, or
  the parent mod of a nested jar). The vanilla agent loads only Blockpilot.
- The GPU details sit in three places: `GlUtil` up to 1.21.1 (OpenGL only),
  the GPU device's `getRenderer()`, `getVendor()`, `getVersion()` and
  `getBackendName()` in 1.21.11 and 26.1, and its `getDeviceInfo()` record
  from 26.2, which added Vulkan and the `preferredGraphicsBackend` option.
- `endpoint.json` is written once the endpoint listens, which is the moment
  agents and scripts wait for. Hashing a large modpack's jars takes a second
  or more.

## Goals / Non-Goals

**Goals:**
- A harness can tell, from one reply, which build of each mod a client
  loaded and which renderer it runs on.
- The hash is of the jar the game loaded, not of whatever the file is now.

**Non-Goals:**
- Window and framebuffer sizes. They belong with the HiDPI capture work,
  where the replies that need them will carry them.
- Hashes in `endpoint.json`, which would hold back the readiness file or
  rewrite it after the fact.
- Hashing folders of a development run, which change as the developer builds.

## Decisions

### D1: Two queries beside status
`client` is one line about the game; `mods` is a row per mod, which runs to
hundreds in a modpack and takes `match=`. `status` stays about the player.
Both are queries, so `assert`, `if` and `wait … until` take them.

### D2: Hash once, in the background, as loading ends
When loading completes, one daemon thread notes each mod file's size and
modification time and then hashes it, so the hash is of the jar the game
loaded unless the file changed within that second. Each `mods` compares the
file's size and time with those noted and marks the row `changed` when they
differ, which is the case of a jar rebuilt under a running client. Hashing
when `mods` first runs was the alternative; it would hash the new jar in
exactly that case. A query can't wait on the client thread, so until the
thread is done `mods` answers `hashing=<files left>` and `wait mods until
hashing==0` serves as the wait.

### D3: The loader's own list, with files where it has them
A new `Services.mods()` gives each mod's id, version, file or folders, and
the mod that carries it when nested. Fabric's origin says this directly.
NeoForge and Forge give a file path that, for a nested jar, lies in another
file system than the default one; such a mod is `nested`, with `in=` where
the loader names the parent file's mod. The vanilla agent lists Blockpilot's
own jar and Minecraft.

### D4: The GPU per version in one place
One method returns the backend, GPU, vendor and driver strings with `//? if`
blocks for the three shapes. `asked=` reads the option's current value; a
change made in the options takes effect at the next start, which the game
says on its options screen.

## Risks / Trade-offs

- A jar replaced in the second between loading and hashing gets the new
  jar's hash. The window is short, and a harness rebuilding while a client
  starts has worse problems.
- `endpoint.json` grows by a line per mod, some tens of kilobytes for a
  large modpack.

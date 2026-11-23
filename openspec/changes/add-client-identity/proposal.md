# Proposal

## Why

A harness that drives several game clients can't tell from Blockpilot which
build of each mod a client loaded, or which renderer it ended up on. In two
days of play tests, a client kept running between runs still carried an
older build of the mod under test, and a later run reused it without
noticing. That voided half a replay. Scripts also grep the game log to learn
whether 26.2 or 26.3 started Vulkan as asked, or fell back to OpenGL, as it
does on a Mac. With the loaded jars' hashes and the backend in a reply, a
harness can refuse the wrong client and assert the render path.

## What Changes

- A new query, `client`: the Minecraft, loader, Blockpilot, API, Java and OS
  versions; the GPU backend the game runs on (`opengl` or `vulkan`) and, from
  26.2, the graphics API the options ask for; the GPU, its vendor and its
  driver as the game reports them; and the number of mods loaded.
- A new query, `mods [match=<pattern>]`: a row per loaded mod with its id,
  version and file, and the file's SHA-256 as it was when the game loaded
  it. A file changed on disk since then is marked `changed`. A mod nested
  in another's file names that mod where the loader tells, as Fabric and
  NeoForge do; a mod a development run loads from folders names the first
  one.
- `endpoint.json` lists the loaded mods with their ids, versions and files,
  so a harness can find which client runs which build without connecting.

## Capabilities

### New Capabilities
- `client-identity`: what a running client is: its versions, its renderer
  and the mod files it loaded.

### Modified Capabilities
- `dev-integration`: the readiness file lists the loaded mods.

## Impact

- `platform/ClientPlatform.java` and each loader's services (NeoForge,
  Forge, Fabric, the vanilla agent): the loaded mods with their files.
- A new `game/identity/` package (the queries, the hashing at load, the
  rows) and the GPU details per version: GlUtil up to 1.21.1, the GPU
  device's getters in 1.21.11 and 26.1, its `DeviceInfo` from 26.2.
- `game/BlockpilotClient.java`: the mods in `endpoint.json`, hashing started
  at load.
- Unit tests for the row format and the change check, smoke checks for both
  queries and the readiness file, `docs/commands.md`, the README command
  table and the help texts.

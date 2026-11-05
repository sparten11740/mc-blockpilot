# Building and releasing

```sh
./gradlew build
```

Gradle runs on JDK 25 and fetches the JDK each build needs. Every jar lands in `build/libs/`, tagged with the Minecraft
version and loader, e.g. `blockpilot-1.0.0-alpha.0+1.21.1-neoforge.jar` or `blockpilot-1.0.0-alpha.0+26.1.2-fabric.jar`.
`./gradlew :blockpilot:1.21.1-neoforge:runClient` (or another `<minecraft>-<loader>` node) starts a dev client; a
`-vanilla` node starts the plain client with the freshly built agent jar.

The mod is one source tree, built with [Stonecutter](https://stonecutter.kikugie.dev/) once per Minecraft version and
loader: each build is a subproject named `<minecraft>-<loader>` under `blockpilot/versions/` with its own
`gradle.properties`. Loader-specific code lives in `dev.blockpilot.platform.neoforge` and `.fabric` and only goes into
that loader's jar; the rest reaches the loader through `ClientPlatform`. A `-vanilla` node builds a Java agent for the
plain client instead of a loader's jar: its `dev.blockpilot.platform.vanilla` (the agent, its Mixin service and the
mixins that stand in for a loader's events) lives in `src/vanilla/`, written against 26.x directly. `dev.blockpilot.api`
and `dev.blockpilot.protocol` are free of Minecraft classes (enforced by a test); `dev.blockpilot.game` holds the
commands. The sources are checked in for `1.21.1-neoforge`. Code that differs between builds is marked with Stonecutter
comments such as `//? if neoforge {` or `//? if >=26.1 {`, and plain renames (`ResourceLocation` to `Identifier`, …) are
rules in `blockpilot/stonecutter.gradle.kts`. What differs most between versions lives in `src/1.21.1/` and `src/26.1/`:
the mixins, and a few classes with the same name and methods in each (`CraftingRecipes`, `FrameCapture`). `src/26.1/`
serves 26.1 and later. These trees and `src/vanilla/` go through Stonecutter too, so the same comments and rules work in
them, but they aren't on every node, so switching the active node leaves them as checked in, for their oldest version.
So a 26.2 or 26.3 node doesn't compile as the active node, in an IDE too: build it by its Gradle path, with another node
active.

To add a target: add it with `target(...)` in `settings.gradle.kts`, create
`blockpilot/versions/<minecraft>-{neoforge,fabric,vanilla}/gradle.properties`, with `version_sources` naming the tree it
builds from, and add rename rules and `//? if` blocks for what changed, as was done for 26.2 and 26.3. Copy a tree only
when a range of versions has diverged too far for that, as 26.1 did from 1.21.1. A rename rule's new text must not
already appear in the shared sources: switching versions applies the rules backwards, which would rename that text too.
Set `sable_companion_version` only for versions Sable publishes its companion API for. In
`.github/workflows/release.yml`, update the number of jars it expects, or the next tag's release fails, and add a row
for each loader jar to upload, or that jar never reaches CurseForge.

Measured on a dev client: `status` round trip over the line protocol about 15-30 ms.

## Testing every target

```sh
python3 tools/test_targets.py
python3 tools/test_targets.py --targets 26.3-fabric,26.3-neoforge
```

One target at a time, this builds it with its unit tests, checks its jar, starts its dev client, runs
`tools/smoke_test.py` in text and JSON mode, and closes the client. It ends with a table of the targets and exits
non-zero if any assertion failed, naming the target and the assertion; a failure doesn't stop the targets after it. JSON
shapes must match those of the other targets of the loader, where the vanilla targets count as Fabric, as saved in
`build/test-targets/<target>/` along with the logs. All the targets take about 25 minutes. Leave the game windows alone
meanwhile: real input takes control back from the test. Ctrl-C closes the client under way, and a target whose client
is already running fails rather than start a second one. It runs on macOS and Linux; on Windows it can't yet stop a
client, so it refuses to start.

`tools/smoke_test.py --game-dir <run dir>` tests a game already running. Besides replies, it checks what commands do in
the game: what each mouse button does to a block and a slot, the camera, the cursor, typing and select-all, a key the
game polls, that frames and videos are upright, the HUD, and perf timings. Recording needs ffmpeg, and recording with
sound the game's audio capture. Without one those checks are reported as skipped, but a target fails if it skips what
another ran.

A script can't check real input taking control back, a real press of F8 (agents are refused it), or installing through
the Minecraft Launcher or the CurseForge app, so those are tested by hand. Whether a video looks right beyond its
orientation and colours is too. So is capture on OpenGL 4.4 or newer, whose buffers work differently from those of a
Mac's OpenGL 4.1. Otherwise Windows and Linux are left to what players report, until someone runs the script on Linux.

## Releasing

Set `mod_version` in `blockpilot/gradle.properties`, add a `## <version> (<date>)` section to `CHANGELOG.md`, commit,
and push a tag `v<version>`. The `release` workflow checks that the tag matches `mod_version`, builds and tests every
target, and uploads the eight loader jars to CurseForge with
[mod-publish-actions](https://github.com/sparten11740/mod-publish-actions); CurseForge has no loader for the vanilla
agent. Once the uploads have gone through, it creates the GitHub release with the eleven jars and the API jar. A version
ending in `-alpha…` or `-beta…` goes out as that release type, anything else as a release. The upload jobs run in the
`release` environment, which holds the secret `CURSEFORGE_TOKEN` and the variable `CURSEFORGE_PROJECT_ID`. When an
upload fails, fix its cause and use Re-run failed jobs on the run: the uploads that went through stay done, and the
GitHub release follows.

[Back to the README](../README.md)

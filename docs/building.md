# Building and releasing

```sh
./gradlew build
```

Gradle runs on JDK 25 and fetches the JDK each build needs. Every jar lands in `build/libs/`, tagged with the Minecraft
version and loader, e.g. `blockpilot-1.0.0+1.21.1-neoforge.jar` or `blockpilot-1.0.0+26.1.2-fabric.jar`. `./gradlew
:blockpilot:1.21.1-neoforge:runClient` (or `1.21.1-fabric`, `26.1-neoforge`, `26.1-fabric`) starts a dev client.

The mod is one source tree, built with [Stonecutter](https://stonecutter.kikugie.dev/) once per Minecraft version and
loader: each build is a subproject named `<minecraft>-<loader>` under `blockpilot/versions/` with its own
`gradle.properties`. Loader-specific code lives in `dev.blockpilot.platform.neoforge` and `.fabric` and only goes into
that loader's jar; the rest reaches the loader through `ClientPlatform`. `dev.blockpilot.api` and
`dev.blockpilot.protocol` are free of Minecraft classes (enforced by a test); `dev.blockpilot.game` holds the commands.
The sources are checked in for `1.21.1-neoforge`. Code that differs between builds is marked with Stonecutter comments
such as `//? if neoforge {` or `//? if >=26.1 {`, and plain renames (`ResourceLocation` to `Identifier`, …) are rules in
`blockpilot/stonecutter.gradle.kts`. What differs most between versions lives in `src/1.21.1/` and `src/26.1/`: the
mixins, and a few classes with the same name and methods in each (`CraftingRecipes`, `FrameCapture`).

To add a target: add it with `target(...)` in `settings.gradle.kts`, create
`blockpilot/versions/<minecraft>-{neoforge,fabric}/gradle.properties` (with `version_sources`), copy the nearest
`src/<version>/`, and add rename rules and `//? if` blocks for what changed, as was done for 26.1. A rename rule's new
text must not already appear in the shared sources: switching versions applies the rules backwards, which would rename
that text too. Set `sable_companion_version` only for versions Sable publishes its companion API for.

Measured on a dev client: `status` round trip over the line protocol about 15-30 ms.

## Releasing

Set `mod_version` in `blockpilot/gradle.properties`, add a `## <version> (<date>)` section to `CHANGELOG.md`, commit,
and push a tag `v<version>`. The `release` workflow checks that the tag matches `mod_version`, builds and tests every
target, creates the GitHub release with the four jars and the API jar, and uploads each jar to Modrinth and CurseForge
with [mod-publish-actions](https://github.com/sparten11740/mod-publish-actions). A version ending in `-alpha…` or
`-beta…` goes out as that release type, anything else as a release. The upload jobs run in the `release` environment,
which holds the secrets `MODRINTH_TOKEN` (`VERSION_CREATE` scope) and `CURSEFORGE_TOKEN` and the variables
`MODRINTH_PROJECT_ID` and `CURSEFORGE_PROJECT_ID`.

[Back to the README](../README.md)

# Using Blockpilot in your mod's dev environment

`./gradlew publishToMavenLocal` publishes `dev.blockpilot:blockpilot-neoforge`, `blockpilot-forge` and
`blockpilot-fabric` (version `<mod version>+<minecraft>`, e.g. `1.0.0+1.20.1`, `1.0.0+1.21.1` or `1.0.0+26.1.2`) and
`dev.blockpilot:blockpilot-api`, the same for every target. In the other project, add `mavenLocal()` to the repositories
and Blockpilot as a runtime-only dependency. `blockpilot.autojoin` joins that world on launch, and
`blockpilot.autocreate=flat|void|normal` creates it first if it is missing.

ModDevGradle (`localRuntime` as in the NeoForge MDK; `runtimeOnly` works too):

```kotlin
dependencies {
    localRuntime("dev.blockpilot:blockpilot-neoforge:1.0.0+1.21.1")
}
neoForge {
    runs {
        named("client") {
            systemProperty("blockpilot.autojoin", "mymod-test")
            systemProperty("blockpilot.autocreate", "flat")
        }
    }
}
```

ModDevGradle's plugin for Forge 1.20.1 (`net.neoforged.moddev.legacyforge`), which remaps the jar to the development
names. Its remapping configurations take a module, not a file: for a jar on disk, add a `flatDir` repository on its
folder and name it as a module there.

```kotlin
dependencies {
    modRuntimeOnly("dev.blockpilot:blockpilot-forge:1.0.0+1.20.1")
}
legacyForge {
    runs {
        named("client") {
            systemProperty("blockpilot.autojoin", "mymod-test")
            systemProperty("blockpilot.autocreate", "flat")
        }
    }
}
```

Fabric Loom (from 26.1, whose Loom doesn't remap mods, `localRuntime` instead of `modLocalRuntime`; on 1.20.1 as on
1.21.1):

```kotlin
dependencies {
    modLocalRuntime("dev.blockpilot:blockpilot-fabric:1.0.0+1.21.1")
}
loom {
    runs {
        named("client") {
            vmArg("-Dblockpilot.autojoin=mymod-test")
            vmArg("-Dblockpilot.autocreate=flat")
        }
    }
}
```

The game directory is then `run`, so the launcher is `run/blockpilot/blockpilot`. From the project directory, this adds
it to Claude Code:

```sh
claude mcp add --transport stdio blockpilot -- "$PWD/run/blockpilot/blockpilot" --mcp
```

`run/blockpilot/blockpilot --wait-ready 300 --wait-world "status"`, run from that project, returns once the player is in
the test world (on the first start, once the game has written the launcher). In this repository the same properties go
through Gradle: `./gradlew :blockpilot:1.21.1-neoforge:runClient -Pblockpilot.autojoin=test
-Pblockpilot.autocreate=flat` (or another node, such as `1.20.1-forge`, `1.21.1-fabric` or `26.3-neoforge`).

[testing.md](testing.md) has recipes for testing your mod this way, from a smoke test to CI.

[Back to the README](../README.md)

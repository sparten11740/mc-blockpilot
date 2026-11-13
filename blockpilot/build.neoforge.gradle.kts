// Builds one NeoForge node of the Stonecutter tree (versions/<minecraft>-neoforge); see stonecutter.gradle.kts.
plugins {
    `java-library`
    `maven-publish`
    id("net.neoforged.moddev")
}

// Each property is read from the gradle.properties that declares it: this build's own (Minecraft and loader versions),
// the tree's (the mod's id, name and version) or the repository's (the licence). Gradle 10 drops looking them up
// through parent projects implicitly.
fun prop(name: String) = property(name).toString()
fun treeProp(name: String) = parent!!.property(name).toString()
fun repoProp(name: String) = rootProject.property(name).toString()

val modId = treeProp("mod_id")
val treeDir = parent!!.projectDir
// Sable publishes its companion API for some Minecraft versions only; a node without one (or built with an empty
// -Psable_companion_version=) leaves the Sable integration out, and the game then runs as without Sable.
val withSable = findProperty("sable_companion_version")?.toString().orEmpty().isNotEmpty()
// The mixin configs this build ships: the one both loaders share (on versions that have one) and the loader's own.
val mixinConfigs = listOf("$modId.mixins.json", "$modId-neoforge.mixins.json").filter { config ->
    listOf("main", prop("version_sources")).any { treeDir.resolve("src/$it/resources/$config").exists() }
}

apply(from = treeDir.resolve("common.gradle.kts"))

// Every jar lands in the root build/libs.
tasks.jar {
    destinationDirectory = rootProject.layout.buildDirectory.dir("libs")
}

sourceSets.main {
    // The other loader's half of the mod (its entry points, event wiring and mixins) is left out of this build.
    java.exclude("dev/blockpilot/platform/fabric/**", "dev/blockpilot/platform/forge/**")
    if (!withSable) {
        java.exclude("dev/blockpilot/compat/sable/**")
    }
}
// What differs too much between Minecraft versions to share with 1.21.1 (mixins): one tree per range.
apply(from = treeDir.resolve("version-sources.gradle.kts"))

repositories {
    mavenCentral()
    // Sable's companion API, which the optional Sable integration compiles against.
    maven("https://maven.ryanhcode.dev/releases") {
        content { includeGroupByRegex("dev\\.ryanhcode.*") }
    }
}

dependencies {
    // Only compiled against: Sable brings the companion when it is installed (see dev.blockpilot.compat.sable).
    if (withSable) {
        compileOnly("dev.ryanhcode.sable-companion:sable-companion-common-${prop("minecraft_version")}:"
            + prop("sable_companion_version")) { isTransitive = false }
    }
}

neoForge {
    version = prop("neo_version")

    // Parameter names for the obfuscated releases up to 1.21.x; from 26.1 on the game ships with its own.
    if (hasProperty("parchment_mappings_version")) {
        parchment {
            minecraftVersion = prop("parchment_minecraft_version")
            mappingsVersion = prop("parchment_mappings_version")
        }
    }

    runs {
        register("client") {
            client()
            // ./gradlew runClient -Pblockpilot.autojoin=<world> [-Pblockpilot.autocreate=flat] joins a world on launch;
            // -Pblockpilot.sable.version=<version> stands in for the Sable companion version found.
            // -Pblockpilot.trustUnverified trusts the config's players on servers that don't verify accounts, as the
            // offline dev accounts' servers don't. -Pblockpilot.devcheck=true registers devcheck:ping for
            // tools/test_targets.py.
            val forwarded = listOf("blockpilot.autojoin", "blockpilot.autocreate", "blockpilot.sable.version",
                "blockpilot.trustUnverified", "blockpilot.devcheck")
            forwarded.forEach { key ->
                providers.gradleProperty(key).orNull?.let { systemProperty(key, it) }
            }
            // NeoForge runs GameTests only in development with this on, for gametest run and its smoke check.
            systemProperty("neoforge.enableGameTest", "true")
        }
    }

    mods {
        register(modId) {
            sourceSet(sourceSets.main.get())
            sourceSets.findByName("gameTests")?.let { sourceSet(it) }
        }
    }
}

val generateModMetadata = tasks.register<ProcessResources>("generateModMetadata") {
    val properties = mapOf(
        "mod_id" to modId,
        "mod_name" to treeProp("mod_name"),
        "mod_version" to treeProp("mod_version"),
        "mod_license" to repoProp("mod_license"),
        "loader_version_range" to prop("loader_version_range"),
        "neo_version" to prop("neo_version"),
        "minecraft_version_range" to prop("minecraft_version_range"),
        "mixin_configs" to mixinConfigs,
    )
    inputs.properties(properties)
    expand(properties)
    from(treeDir.resolve("src/main/templates")) {
        include("META-INF/neoforge.mods.toml")
    }
    into(layout.buildDirectory.dir("generated/sources/modMetadata"))
}

sourceSets.main {
    resources.srcDir(generateModMetadata)
}
neoForge.ideSyncTask(generateModMetadata)

tasks.processResources {
    exclude("$modId-fabric.mixins.json", "$modId.accesswidener")
}

apply(from = treeDir.resolve("coverage.gradle.kts"))
apply(from = treeDir.resolve("baritone.gradle.kts"))

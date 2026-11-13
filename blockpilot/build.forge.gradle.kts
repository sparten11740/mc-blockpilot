// Builds one Forge node of the Stonecutter tree (versions/<minecraft>-forge); see stonecutter.gradle.kts. Forge is built
// only for 1.20.1, with ModDevGradle's plugin for Forge before NeoForge split off.
plugins {
    `java-library`
    `maven-publish`
    id("net.neoforged.moddev.legacyforge")
}

// Each property is read from the gradle.properties that declares it: this build's own (Minecraft and loader versions),
// the tree's (the mod's id, name and version) or the repository's (the licence).
fun prop(name: String) = property(name).toString()
fun treeProp(name: String) = parent!!.property(name).toString()
fun repoProp(name: String) = rootProject.property(name).toString()

val modId = treeProp("mod_id")
val treeDir = parent!!.projectDir
val accessTransformer = treeDir.resolve("src/${prop("version_sources")}/accesstransformer.cfg")
// The mixin configs this build ships: the one all loaders share and Forge's own, where the version has them.
val mixinConfigs = listOf("$modId.mixins.json", "$modId-forge.mixins.json").filter { config ->
    listOf("main", prop("version_sources")).any { treeDir.resolve("src/$it/resources/$config").exists() }
}

apply(from = treeDir.resolve("common.gradle.kts"))

sourceSets.main {
    // The other loaders' halves of the mod (their entry points, event wiring and mixins) are left out of this build,
    // and so is Sable, which has no 1.20.1 companion.
    java.exclude("dev/blockpilot/platform/fabric/**", "dev/blockpilot/platform/neoforge/**",
        "dev/blockpilot/compat/sable/**")
}
// What differs too much between Minecraft versions to share with 1.21.1 (mixins): one tree per range.
apply(from = treeDir.resolve("version-sources.gradle.kts"))

repositories {
    mavenCentral()
}

// ./gradlew :blockpilot:1.20.1-forge:runClient -Pblockpilot.neoforge=1.20.1-47.1.106 builds and runs the node on that
// NeoForge 47.1 instead, which loads Forge 1.20.1 mods: a check that the mod works there too.
val neoForge = providers.gradleProperty("blockpilot.neoforge").orNull

legacyForge {
    if (neoForge != null) {
        enable { neoForgeVersion = neoForge }
    } else {
        version = "${prop("minecraft_version")}-${prop("forge_version")}"
    }
    // Opens the vanilla types blockpilot.accesswidener opens on Fabric; kept outside the resources, which Stonecutter
    // generates after the game's artifacts read it, and copied into the jar below.
    accessTransformers.from(accessTransformer)

    parchment {
        minecraftVersion = prop("parchment_minecraft_version")
        mappingsVersion = prop("parchment_mappings_version")
    }

    runs {
        register("client") {
            client()
            // The same switches as the other loaders' client runs; see build.neoforge.gradle.kts.
            val forwarded = listOf("blockpilot.autojoin", "blockpilot.autocreate", "blockpilot.sable.version",
                "blockpilot.trustUnverified", "blockpilot.devcheck")
            forwarded.forEach { key ->
                providers.gradleProperty(key).orNull?.let { systemProperty(key, it) }
            }
            // Forge runs GameTests only in development with this on, for gametest run and its smoke check.
            systemProperty("forge.enableGameTest", "true")
        }
    }

    mods {
        register(modId) {
            sourceSet(sourceSets.main.get())
            sourceSets.findByName("gameTests")?.let { sourceSet(it) }
        }
    }
}

// The plugin's game artifacts read the main resources, which include the trees Stonecutter writes.
tasks.named("createMinecraftArtifacts") {
    dependsOn(tasks.matching { it.name.startsWith("stonecutterGenerate") })
}

// Forge runs mods with the game's intermediate (SRG) names, so mixins need a refmap from development names to those.
mixin {
    add(sourceSets.main.get(), "$modId.refmap.json")
    mixinConfigs.forEach { config(it) }
}

dependencies {
    annotationProcessor("org.spongepowered:mixin:${prop("mixin_version")}:processor")
    // Forge 47 doesn't bundle MixinExtras, which some of the shared mixins use: the jar carries it, and Forge loads
    // the one copy with the highest version among all mods.
    compileOnly(annotationProcessor("io.github.llamalad7:mixinextras-common:${prop("mixinextras_version")}")!!)
    implementation("io.github.llamalad7:mixinextras-forge:${prop("mixinextras_version")}")
    jarJar("io.github.llamalad7:mixinextras-forge:${prop("mixinextras_version")}")
    // @Nullable, which Forge doesn't bring along at compile time.
    compileOnly("com.google.code.findbugs:jsr305:3.0.2")
}

val generateModMetadata = tasks.register<ProcessResources>("generateModMetadata") {
    val properties = mapOf(
        "mod_id" to modId,
        "mod_name" to treeProp("mod_name"),
        "mod_version" to treeProp("mod_version"),
        "mod_license" to repoProp("mod_license"),
        "loader_version_range" to prop("loader_version_range"),
        "forge_version_range" to prop("forge_version_range"),
        "minecraft_version_range" to prop("minecraft_version_range"),
        "pack_format" to prop("pack_format"),
    )
    inputs.properties(properties)
    expand(properties)
    from(treeDir.resolve("src/main/templates")) {
        include("META-INF/mods.toml", "pack.mcmeta")
    }
    into(layout.buildDirectory.dir("generated/sources/modMetadata"))
}

sourceSets.main {
    resources.srcDir(generateModMetadata)
}
legacyForge.ideSyncTask(generateModMetadata)

tasks.processResources {
    exclude("$modId-fabric.mixins.json", "$modId-neoforge.mixins.json", "$modId.accesswidener")
    from(accessTransformer) { into("META-INF") }
}

// The jar a player installs is the one with the game's intermediate names; the development one stays beside the build.
// Forge 1.20.1 finds a mod's mixin configs in its manifest, which the remapped jar takes from this one.
tasks.named<Jar>("jar") {
    archiveClassifier = "dev"
    manifest.attributes("MixinConfigs" to mixinConfigs.joinToString(","))
}
tasks.named<Jar>("reobfJar") {
    archiveClassifier = ""
    destinationDirectory = rootProject.layout.buildDirectory.dir("libs")
}

apply(from = treeDir.resolve("coverage.gradle.kts"))

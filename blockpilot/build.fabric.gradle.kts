// Builds one Fabric node of the Stonecutter tree (versions/<minecraft>-fabric); see stonecutter.gradle.kts.
plugins {
    `java-library`
    `maven-publish`
    // Applies Fabric Loom: the remapping variant up to 1.21.x, the plain one for the unobfuscated 26.x releases.
    id("dev.kikugie.loom-back-compat")
}

// Each property is read from the gradle.properties that declares it: this build's own (Minecraft and loader versions),
// the tree's (the mod's id, name and version) or the repository's (the licence). Gradle 10 drops looking them up
// through parent projects implicitly.
fun prop(name: String) = property(name).toString()
fun treeProp(name: String) = parent!!.property(name).toString()
fun repoProp(name: String) = rootProject.property(name).toString()

val modId = treeProp("mod_id")
val minecraft = prop("minecraft_version")
val treeDir = parent!!.projectDir
// Sable publishes its companion API for some Minecraft versions only; a node without one (or built with an empty
// -Psable_companion_version=) leaves the Sable integration out, and the game then runs as without Sable.
val withSable = findProperty("sable_companion_version")?.toString().orEmpty().isNotEmpty()
// The mixin configs this build ships: the one both loaders share (on versions that have one) and the loader's own.
val mixinConfigs = listOf("$modId.mixins.json", "$modId-fabric.mixins.json").filter { config ->
    listOf("main", prop("version_sources")).any { treeDir.resolve("src/$it/resources/$config").exists() }
}
// Opens vanilla types NeoForge already makes public; one per version range, as the names differ between versions.
val accessWidener = treeDir.resolve("src/${prop("version_sources")}/resources/$modId.accesswidener")

apply(from = treeDir.resolve("common.gradle.kts"))

// Every jar lands in the root build/libs.
loomx.modJar {
    destinationDirectory = rootProject.layout.buildDirectory.dir("libs")
}

sourceSets.main {
    // The other loader's half of the mod (its entry points, event wiring and mixins) is left out of this build.
    java.exclude("dev/blockpilot/platform/neoforge/**")
    if (!withSable) {
        java.exclude("dev/blockpilot/compat/sable/**")
    }
}
// What differs too much between Minecraft versions to share with 1.21.1 (mixins): one tree per range.
apply(from = treeDir.resolve("version-sources.gradle.kts"))

repositories {
    maven("https://maven.parchmentmc.org") {
        content { includeGroup("org.parchmentmc.data") }
    }
    mavenCentral()
    // Sable's companion API, which the optional Sable integration compiles against.
    maven("https://maven.ryanhcode.dev/releases") {
        content { includeGroupByRegex("dev\\.ryanhcode.*") }
    }
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraft")
    // Releases from 26.1 on ship with Mojang's names; older ones are mapped to them, with Parchment's parameter names.
    if (!project.loomx.isUnobfuscated) {
        mappings(loom.layered {
            officialMojangMappings()
            parchment("org.parchmentmc.data:parchment-${prop("parchment_minecraft_version")}:${prop("parchment_mappings_version")}@zip")
        })
    }
    modImplementation("net.fabricmc:fabric-loader:${prop("fabric_loader_version")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${prop("fabric_api_version")}")
    // @Nullable, which NeoForge brings along and Fabric does not.
    compileOnly("com.google.code.findbugs:jsr305:3.0.2")
    // Only compiled against: Sable brings the companion when it is installed (see dev.blockpilot.compat.sable).
    if (withSable) {
        modCompileOnly("dev.ryanhcode.sable-companion:sable-companion-fabric-${prop("minecraft_version")}:"
            + prop("sable_companion_version")) { isTransitive = false }
    }

}

loom {
    if (accessWidener.exists()) {
        accessWidenerPath = accessWidener
    }
    runs {
        named("client") {
            runDirectory = layout.projectDirectory.dir("run")
            // ./gradlew runClient -Pblockpilot.autojoin=<world> [-Pblockpilot.autocreate=flat] joins a world on launch;
            // -Pblockpilot.sable.version=<version> stands in for the Sable companion version found.
            // -Pblockpilot.trustUnverified trusts the config's players on servers that don't verify accounts, as the
            // offline dev accounts' servers don't. -Pblockpilot.devcheck=true registers devcheck:ping for
            // tools/test_targets.py.
            val forwarded = listOf("blockpilot.autojoin", "blockpilot.autocreate", "blockpilot.sable.version",
                "blockpilot.trustUnverified", "blockpilot.devcheck")
            forwarded.forEach { key ->
                providers.gradleProperty(key).orNull?.let { property(key, it) }
            }
        }
    }
    // Blockpilot's own GameTests (see common.gradle.kts) join the mod in the dev runs.
    sourceSets.findByName("gameTests")?.let { gameTests ->
        mods {
            register(modId) {
                sourceSet(sourceSets.main.get())
                sourceSet(gameTests)
            }
        }
        runs.named("client") { source(gameTests) }
    }
}

val generateModMetadata = tasks.register<ProcessResources>("generateModMetadata") {
    val properties = mapOf(
        "mod_id" to modId,
        "mod_name" to treeProp("mod_name"),
        "mod_version" to treeProp("mod_version"),
        "mod_license" to repoProp("mod_license"),
        "fabric_loader_min_version" to prop("fabric_loader_min_version"),
        "fabric_api_version" to prop("fabric_api_version").substringBefore('+'),
        "minecraft_version_range" to prop("minecraft_version_range"),
        "java_version" to prop("java_version"),
        "mixin_configs" to mixinConfigs,
        "access_widener" to if (accessWidener.exists()) accessWidener.name else "",
    )
    inputs.properties(properties)
    expand(properties)
    from(treeDir.resolve("src/main/templates")) {
        include("fabric.mod.json")
    }
    into(layout.buildDirectory.dir("generated/sources/modMetadata"))
}

sourceSets.main {
    resources.srcDir(generateModMetadata)
}

tasks.processResources {
    exclude("$modId-neoforge.mixins.json")
}

apply(from = treeDir.resolve("coverage.gradle.kts"))
// Remapping a mod dependency to the development names drops the jars it declares nested; baritone.gradle.kts adds them.
extra["remapsMods"] = !loomx.isUnobfuscated
apply(from = treeDir.resolve("baritone.gradle.kts"))

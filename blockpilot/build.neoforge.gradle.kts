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
val minecraft = prop("minecraft_version")
val treeDir = parent!!.projectDir
// Sable publishes its companion API for some Minecraft versions only; a node without one (or built with an empty
// -Psable_companion_version=) leaves the Sable integration out, and the game then runs as without Sable.
val withSable = findProperty("sable_companion_version")?.toString().orEmpty().isNotEmpty()
// The mixin configs this build ships: the one both loaders share (on versions that have one) and the loader's own.
val mixinConfigs = listOf("$modId.mixins.json", "$modId-neoforge.mixins.json").filter { config ->
    listOf("main", prop("version_sources")).any { treeDir.resolve("src/$it/resources/$config").exists() }
}

version = "${treeProp("mod_version")}+$minecraft-neoforge"
group = treeProp("mod_group_id")

base {
    archivesName = modId
}

// Every jar lands in the root build/libs.
tasks.jar {
    destinationDirectory = rootProject.layout.buildDirectory.dir("libs")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(prop("java_version").toInt())
}

sourceSets.main {
    // The other loader's half of the mod (its entry points, event wiring and mixins) is left out of this build.
    java.exclude("dev/blockpilot/platform/fabric/**")
    if (!withSable) {
        java.exclude("dev/blockpilot/compat/sable/**")
    }
    // What differs too much between Minecraft versions for inline conditions (mixins): one copy per range.
    java.srcDir(treeDir.resolve("src/${prop("version_sources")}/java"))
    resources.srcDir(treeDir.resolve("src/${prop("version_sources")}/resources"))
}

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
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    // The protocol layer's tests run without the game; these are the versions Minecraft 1.21.1 ships.
    testImplementation("com.google.code.gson:gson:2.10.1")
    testImplementation("io.netty:netty-handler:4.1.97.Final")
    testImplementation("org.slf4j:slf4j-api:2.0.9")
    testImplementation("org.apache.logging.log4j:log4j-core:2.22.1")
    testImplementation("org.joml:joml:1.10.5")
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
            listOf("blockpilot.autojoin", "blockpilot.autocreate", "blockpilot.sable.version").forEach { key ->
                providers.gradleProperty(key).orNull?.let { systemProperty(key, it) }
            }
        }
    }

    mods {
        register(modId) {
            sourceSet(sourceSets.main.get())
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

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.test {
    useJUnitPlatform()
}

apply(from = treeDir.resolve("coverage.gradle.kts"))

// mavenLocal() coordinates for other mods' dev environments: dev.blockpilot:blockpilot-neoforge:<version>+<minecraft>.
publishing {
    publications {
        register<MavenPublication>("mod") {
            artifactId = "$modId-neoforge"
            version = "${treeProp("mod_version")}+$minecraft"
            from(components["java"])
        }
    }
}

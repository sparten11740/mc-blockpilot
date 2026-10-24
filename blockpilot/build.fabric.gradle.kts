// Builds one Fabric node of the Stonecutter tree (versions/<minecraft>-fabric); see stonecutter.gradle.kts.
plugins {
    `java-library`
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
// The mixin configs this build ships: the one both loaders share (on versions that have one) and the loader's own.
val mixinConfigs = listOf("$modId.mixins.json", "$modId-fabric.mixins.json").filter { config ->
    listOf("main", prop("version_sources")).any { treeDir.resolve("src/$it/resources/$config").exists() }
}

version = "${treeProp("mod_version")}+$minecraft-fabric"
group = treeProp("mod_group_id")

base {
    archivesName = modId
}

// Every jar lands in the root build/libs.
loomx.modJar {
    destinationDirectory = rootProject.layout.buildDirectory.dir("libs")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(prop("java_version").toInt())
}

sourceSets.main {
    // The other loader's half of the mod (its entry points, event wiring and mixins) is left out of this build.
    java.exclude("dev/blockpilot/platform/neoforge/**")
    // What differs too much between Minecraft versions for inline conditions (mixins): one copy per range.
    java.srcDir(treeDir.resolve("src/${prop("version_sources")}/java"))
    resources.srcDir(treeDir.resolve("src/${prop("version_sources")}/resources"))
}

repositories {
    maven("https://maven.parchmentmc.org") {
        content { includeGroup("org.parchmentmc.data") }
    }
    mavenCentral()
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

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

loom {
    runs {
        named("client") {
            runDirectory = layout.projectDirectory.dir("run")
        }
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

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.test {
    useJUnitPlatform()
}

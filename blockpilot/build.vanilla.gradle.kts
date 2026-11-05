// Builds one vanilla node of the Stonecutter tree (versions/<minecraft>-vanilla): a Java agent for the plain client,
// with no mod loader; see stonecutter.gradle.kts. Minecraft comes from ModDevGradle's vanilla mode.
plugins {
    `java-library`
    id("net.neoforged.moddev")
}

fun prop(name: String) = property(name).toString()
fun treeProp(name: String) = parent!!.property(name).toString()

val modId = treeProp("mod_id")
val minecraft = prop("minecraft_version")
val treeDir = parent!!.projectDir

version = "${treeProp("mod_version")}+$minecraft-vanilla"
group = treeProp("mod_group_id")

base {
    archivesName = modId
}

tasks.jar {
    destinationDirectory = rootProject.layout.buildDirectory.dir("libs")
    from(rootProject.files("LICENSE", "NOTICE")) { rename { "${it}_$modId" } }
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(prop("java_version").toInt())
}

sourceSets.main {
    // The loaders' halves of the mod, and the Sable integration, which needs a loader for Sable, are left out.
    java.exclude("dev/blockpilot/platform/neoforge/**", "dev/blockpilot/platform/fabric/**", "dev/blockpilot/compat/sable/**")
}
// The version sources, and the vanilla platform, written against 26.x directly like them.
apply(from = treeDir.resolve("version-sources.gradle.kts"))

// The agent's entry class, built for Java 8 so it loads on any game's runtime and can turn a wrong version away.
val agent: SourceSet = sourceSets.create("agent") {
    java.setSrcDirs(listOf(treeDir.resolve("src/vanilla/agent")))
}
tasks.named<JavaCompile>(agent.compileJavaTaskName) {
    options.release = 8
    options.compilerArgs.add("-Xlint:-options")
}

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/") {
        content { includeGroup("net.fabricmc") }
    }
}

// What the agent brings along, since a vanilla client has none of it: Mixin, MixinExtras, ASM and the access widener.
val bundled: Configuration by configurations.creating
val asmVersion = "9.11"
configurations.implementation {
    extendsFrom(bundled)
}

dependencies {
    bundled("net.fabricmc:sponge-mixin:0.17.5+mixin.0.8.7") {
        isTransitive = false
    }
    listOf("asm", "asm-tree", "asm-commons", "asm-util", "asm-analysis").forEach { bundled("org.ow2.asm:$it:$asmVersion") }
    bundled("io.github.llamalad7:mixinextras-common:0.5.5")
    bundled("net.fabricmc:access-widener:2.1.0") {
        isTransitive = false
    }
    // The shared tests, as in the loader builds.
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("com.google.code.gson:gson:2.10.1")
    testImplementation("io.netty:netty-handler:4.1.97.Final")
    testImplementation("org.slf4j:slf4j-api:2.0.9")
    testImplementation("org.apache.logging.log4j:log4j-core:2.22.1")
    testImplementation("org.joml:joml:1.10.5")
    testImplementation("de.bottlecaps.rr:rr-lib:2.6") {
        exclude(group = "org.apache.xmlgraphics")
    }
}

neoForge {
    neoFormVersion = prop("neoform_version")
    accessTransformers.from(treeDir.resolve("src/vanilla/accesstransformer.cfg"))

    runs {
        register("client") {
            client()
            jvmArguments.add(tasks.jar.flatMap { it.archiveFile }.map { "-javaagent:${it.asFile.absolutePath}" })
            listOf("blockpilot.autojoin", "blockpilot.autocreate").forEach { key ->
                providers.gradleProperty(key).orNull?.let { systemProperty(key, it) }
            }
        }
    }
}

tasks.named("runClient") {
    dependsOn(tasks.jar)
}

tasks.jar {
    manifest {
        attributes(
            "Premain-Class" to "dev.blockpilot.platform.vanilla.BlockpilotAgent",
            "Implementation-Title" to treeProp("mod_name"),
            "Implementation-Version" to treeProp("mod_version"),
            "Blockpilot-Minecraft" to minecraft,
        )
        // Mixin reads ASM's version from its package's manifest entry, which bundling would otherwise drop, and
        // without it refuses Java 21 mixin configs.
        attributes(mapOf("Implementation-Version" to asmVersion), "org/objectweb/asm/")
    }
    // The project's own services win over the bundled libraries' ones, which name loaders this client doesn't have.
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(agent.output)
    // The licenses of the bundled libraries whose jars carry none; Mixin's and MixinExtras' come with their classes.
    from(treeDir.resolve("src/vanilla/licenses"))
    from(bundled.map { zipTree(it) }) {
        exclude("module-info.class", "META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/MANIFEST.MF",
            "META-INF/services/javax.annotation.processing.Processor", "META-INF/services/cpw.mods.*",
            "META-INF/services/org.spongepowered.tools.*")
    }
}

tasks.test {
    useJUnitPlatform()
    // The grammar tests read the specs, the active changes and the grammar file, so editing them reruns the tests.
    inputs.dir(rootProject.file("openspec")).withPropertyName("openspec")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(rootProject.file("docs/grammar.ebnf"), rootProject.file("docs/grammar.xhtml"))
        .withPropertyName("grammarFile").withPathSensitivity(PathSensitivity.RELATIVE)
    // Other tests read every source tree and the rename rules, not only what this node builds.
    inputs.dir(treeDir.resolve("src")).withPropertyName("sourceTrees").withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(treeDir.resolve("stonecutter.gradle.kts")).withPropertyName("renameRules")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    // Mixin's annotation processor writes refmaps for obfuscated names, which 26.1 doesn't have.
    options.compilerArgs.add("-proc:none")
}

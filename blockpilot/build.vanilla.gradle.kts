// Builds the vanilla node of the Stonecutter tree (versions/26.1-vanilla): a Java agent for the plain client, with no
// mod loader; see stonecutter.gradle.kts. Minecraft comes from ModDevGradle's vanilla mode.
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
    from(rootProject.file("LICENSE")) { rename { "LICENSE_$modId" } }
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(prop("java_version").toInt())
}

sourceSets.main {
    // The loaders' halves of the mod, and the Sable integration, which needs a loader for Sable, are left out.
    java.exclude("dev/blockpilot/platform/neoforge/**", "dev/blockpilot/platform/fabric/**", "dev/blockpilot/compat/sable/**")
    java.srcDir(treeDir.resolve("src/${prop("version_sources")}/java"))
    resources.srcDir(treeDir.resolve("src/${prop("version_sources")}/resources"))
    // The vanilla platform, written against 26.1 directly like the version sources.
    java.srcDir(treeDir.resolve("src/vanilla/java"))
    resources.srcDir(treeDir.resolve("src/vanilla/resources"))
}

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
        )
        // Mixin reads ASM's version from its package's manifest entry, which bundling would otherwise drop, and
        // without it refuses Java 21 mixin configs.
        attributes(mapOf("Implementation-Version" to asmVersion), "org/objectweb/asm/")
    }
    // The project's own services win over the bundled libraries' ones, which name loaders this client doesn't have.
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(agent.output)
    from(bundled.map { zipTree(it) }) {
        exclude("module-info.class", "META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/MANIFEST.MF",
            "META-INF/services/javax.annotation.processing.Processor", "META-INF/services/cpw.mods.*",
            "META-INF/services/org.spongepowered.tools.*")
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    // Mixin's annotation processor writes refmaps for obfuscated names, which 26.1 doesn't have.
    options.compilerArgs.add("-proc:none")
}

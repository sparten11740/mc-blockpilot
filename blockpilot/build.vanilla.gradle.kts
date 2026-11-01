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
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(prop("java_version").toInt())
}

sourceSets.main {
    java.include("dev/blockpilot/platform/vanilla/**")
    resources.setSrcDirs(listOf(treeDir.resolve("src/vanilla/resources")))
}

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/") {
        content { includeGroup("net.fabricmc") }
    }
}

// What the agent brings along, since a vanilla client has none of it: Mixin, MixinExtras, ASM and the access widener.
val bundled: Configuration by configurations.creating
configurations.implementation {
    extendsFrom(bundled)
}

dependencies {
    bundled("net.fabricmc:sponge-mixin:0.17.5+mixin.0.8.7") {
        isTransitive = false
    }
    listOf("asm", "asm-tree", "asm-commons", "asm-util", "asm-analysis").forEach { bundled("org.ow2.asm:$it:9.11") }
}

neoForge {
    neoFormVersion = prop("neoform_version")

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
    }
    // The project's own services win over the bundled libraries' ones, which name loaders this client doesn't have.
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
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

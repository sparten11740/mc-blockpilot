// Baritone in development runs: ./gradlew runClient -Pblockpilot.baritone puts the Baritone build for this node's
// Minecraft version (baritone_version), from cabaletta's GitHub releases and checked against the SHA-1 they publish
// (baritone_sha1), on the client's runtime classpath only, so goto and mine walk through it. It is never part of the
// published jar or its metadata. Applied by the NeoForge and Fabric builds; a node without baritone_version has none.
val baritoneVersion = findProperty("baritone_version")?.toString()
val baritoneSha1 = findProperty("baritone_sha1")?.toString()
val withBaritone = providers.gradleProperty("blockpilot.baritone").map { it != "false" }.getOrElse(false)
    && baritoneVersion != null
val loader = if (pluginManager.hasPlugin("net.neoforged.moddev")) "neoforge" else "fabric"

if (withBaritone) {
    requireNotNull(baritoneSha1) { "baritone_version=$baritoneVersion has no baritone_sha1 beside it to check it by" }
    repositories {
        exclusiveContent {
            forRepository {
                ivy {
                    name = "Baritone releases"
                    url = uri("https://github.com/cabaletta/baritone/releases/download")
                    patternLayout { artifact("v[revision]/[module]-[revision].[ext]") }
                    metadataSources { artifact() }
                }
            }
            filter { includeGroup("cabaletta") }
        }
    }
    val coordinates = "cabaletta:baritone-api-$loader:$baritoneVersion"
    val baritoneJar = configurations.create("baritoneJar") { isTransitive = false }
    dependencies {
        baritoneJar(coordinates)
    }
    val checkBaritone = tasks.register("checkBaritone") {
        val jar = baritoneJar
        val expected = baritoneSha1
        doLast {
            val file = jar.singleFile
            val actual = java.security.MessageDigest.getInstance("SHA-1").digest(file.readBytes())
                .joinToString("") { "%02x".format(it) }
            check(actual == expected) { "${file.name} has SHA-1 $actual, not the $expected its release publishes" }
        }
    }
    if (loader == "fabric") {
        // Fabric remaps a mod to the development names, local to the runs.
        dependencies { add("modLocalRuntime", coordinates) }
        if (extra.has("remapsMods") && extra["remapsMods"] == true) {
            // The remapped copy no longer declares the jars nested in it (Baritone's nether-pathfinder), so the run
            // takes them from the checked jar as plain libraries.
            val nestedDir = layout.buildDirectory.dir("baritone-nested")
            val nested = tasks.register<Sync>("baritoneNestedJars") {
                dependsOn(checkBaritone)
                from({ zipTree(baritoneJar.singleFile) }) {
                    include("META-INF/jars/*.jar")
                    eachFile { path = name }
                }
                includeEmptyDirs = false
                into(nestedDir)
            }
            dependencies { add("localRuntime", fileTree(nestedDir) { include("*.jar"); builtBy(nested) }) }
        }
    } else {
        // NeoForge runs it as it is; only the run's classpath takes it, not the published variants.
        configurations.named("runtimeClasspath") { extendsFrom(baritoneJar) }
    }
    tasks.matching { it.name == "runClient" }.configureEach { dependsOn(checkBaritone) }
}

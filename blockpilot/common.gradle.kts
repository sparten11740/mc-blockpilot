// What every node's build does alike: its identity, Java, licence files and tests, and for those that publish, its
// coordinates. The loader's own build script applies this and keeps what only that loader needs.
val modId = parent!!.property("mod_id").toString()
val modVersion = parent!!.property("mod_version").toString()
val minecraft = property("minecraft_version").toString()
val loader = name.substringAfterLast('-')
val treeDir = parent!!.projectDir

version = "$modVersion+$minecraft-$loader"
group = parent!!.property("mod_group_id").toString()

the<BasePluginExtension>().archivesName = modId

the<JavaPluginExtension>().toolchain.languageVersion =
    JavaLanguageVersion.of(property("java_version").toString().toInt())

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

// Both licenses, named so they can't clash with another jar's.
tasks.named<Jar>("jar") {
    from(rootProject.files("LICENSE-APACHE", "LICENSE-MIT")) { rename { "${it}_$modId" } }
}

dependencies {
    "testImplementation"(platform("org.junit:junit-bom:5.11.4"))
    "testImplementation"("org.junit.jupiter:junit-jupiter")
    "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    // The protocol layer's tests run without the game; these are the versions Minecraft 1.21.1 ships.
    "testImplementation"("com.google.code.gson:gson:2.10.1")
    "testImplementation"("io.netty:netty-handler:4.1.97.Final")
    "testImplementation"("org.slf4j:slf4j-api:2.0.9")
    "testImplementation"("org.apache.logging.log4j:log4j-core:2.22.1")
    "testImplementation"("org.joml:joml:1.10.5")
    // The grammar file test parses docs/grammar.ebnf with the Railroad Diagram Generator's W3C EBNF parser.
    "testImplementation"("de.bottlecaps.rr:rr-lib:2.6") {
        exclude(group = "org.apache.xmlgraphics")
    }
}

// A minimal integration compiled against blockpilot-api 2.0.0 as attached to the v1.0.0 release, not against the
// current source; the extension API test loads it against the current API, so integrations of 2.0.0 keep working.
repositories {
    exclusiveContent {
        forRepository {
            ivy {
                name = "Blockpilot releases"
                url = uri("https://github.com/sparten11740/mc-blockpilot/releases/download")
                patternLayout { artifact("v1.0.0/[module]-[revision].[ext]") }
                metadataSources { artifact() }
            }
        }
        filter { includeModule("dev.blockpilot", "blockpilot-api") }
    }
}
val apiFixture: SourceSet = the<SourceSetContainer>().create("apiFixture") {
    java.setSrcDirs(listOf(treeDir.resolve("src/apiFixture/java")))
    resources.setSrcDirs(listOf<File>())
}
dependencies {
    apiFixture.compileOnlyConfigurationName("dev.blockpilot:blockpilot-api:2.0.0")
    "testRuntimeOnly"(apiFixture.output)
}
// Blockpilot's own GameTests on 1.21.1, where tests are code (26.x takes the smoke check's data pack instead). The
// builds add them to the mod in the dev runs only, so the published jar never has them.
if (property("version_sources").toString() == "1.21.1") {
    val main = the<SourceSetContainer>()["main"]
    val gameTests = the<SourceSetContainer>().create("gameTests") {
        java.setSrcDirs(listOf(treeDir.resolve("src/gameTests/java")))
        resources.setSrcDirs(listOf<File>())
        compileClasspath += main.output + main.compileClasspath
        runtimeClasspath += output + main.output + main.runtimeClasspath
    }
    // Only the dev runs use them, so the build compiles them here to catch a break without a client.
    tasks.named("check") { dependsOn(gameTests.classesTaskName) }
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    // The grammar tests read the specs, the active changes and the grammar file, so editing them reruns the tests.
    inputs.dir(rootProject.file("openspec")).withPropertyName("openspec")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(rootProject.file("docs/grammar.ebnf"), rootProject.file("docs/grammar.xhtml"))
        .withPropertyName("grammarFile").withPathSensitivity(PathSensitivity.RELATIVE)
    // Other tests read every source tree, the rename rules and the checked-in node, not only what this node builds.
    inputs.dir(treeDir.resolve("src")).withPropertyName("sourceTrees").withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(treeDir.resolve("stonecutter.gradle.kts")).withPropertyName("renameRules")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(rootProject.file("settings.gradle.kts")).withPropertyName("checkedInNode")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}

// mavenLocal() coordinates for other mods' dev environments: dev.blockpilot:blockpilot-<loader>:<version>+<minecraft>.
pluginManager.withPlugin("maven-publish") {
    configure<PublishingExtension> {
        publications {
            register<MavenPublication>("mod") {
                artifactId = "$modId-$loader"
                version = "$modVersion+$minecraft"
                from(components["java"])
            }
        }
    }
}

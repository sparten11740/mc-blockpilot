// The extension API alone, for other mods to compile against: dev.blockpilot.api from the mod's own sources. It holds
// no Minecraft or loader types, so this one artifact serves every Minecraft version and loader.
plugins {
    `java-library`
    `maven-publish`
}

group = "dev.blockpilot"
version = rootProject.property("api_version").toString()

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
    withSourcesJar()
}

sourceSets.main {
    java.setSrcDirs(listOf(rootProject.file("blockpilot/src/main/java")))
    java.include("dev/blockpilot/api/**")
}

repositories {
    mavenCentral()
}

dependencies {
    // Provided by the game at runtime.
    compileOnly("org.slf4j:slf4j-api:2.0.9")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    // Mods on Minecraft 1.20.1, which is played on Java 17, compile against it too.
    options.release = 17
}

tasks.jar {
    from(rootProject.files("LICENSE-APACHE", "LICENSE-MIT")) { rename { "${it}_blockpilot-api" } }
}

publishing {
    publications {
        register<MavenPublication>("api") {
            artifactId = "blockpilot-api"
            from(components["java"])
        }
    }
}

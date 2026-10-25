pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/") { name = "Fabric" }
        maven("https://maven.neoforged.net/releases/") { name = "NeoForged" }
        maven("https://maven.kikugie.dev/releases") { name = "KikuGie" }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    id("dev.kikugie.stonecutter") version "0.9.8"
}

rootProject.name = "blockpilot-monorepo"

// The mod is built once per Minecraft version and loader from the one source tree: each node is named
// <minecraft>-<loader> and built by that loader's script. See blockpilot/stonecutter.gradle.kts.
stonecutter {
    create("blockpilot") {
        fun target(minecraft: String, vararg loaders: String) = loaders.forEach { loader ->
            version("$minecraft-$loader", minecraft).buildscript = "build.$loader.gradle.kts"
        }

        target("1.21.1", "neoforge", "fabric")
        target("26.1", "neoforge", "fabric")

        vcsVersion = "1.21.1-neoforge"
    }
}

// The extension API on its own, published for other mods to compile against.
include("blockpilot-api")

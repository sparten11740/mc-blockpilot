// Each subproject applies what it needs; the plugin versions are pinned once here.
plugins {
    id("net.neoforged.moddev") version "2.0.148" apply false
    // Fabric Loom comes in two variants: the remapping one for the obfuscated releases up to 1.21.x and the plain one
    // for 26.1 and newer, which ship unobfuscated. Loom back-compat applies the right one to each Fabric node.
    id("net.fabricmc.fabric-loom-remap") version "1.19.1" apply false
    id("net.fabricmc.fabric-loom") version "1.19.1" apply false
    id("dev.kikugie.loom-back-compat") version "0.4.3" apply false
}

tasks.named<Wrapper>("wrapper") {
    distributionType = Wrapper.DistributionType.BIN
}

repositories {
    mavenCentral()
}

// The Railroad Diagram Generator's library, only for drawing the grammar; Batik is for PNG output, which isn't used.
val railroad: Configuration by configurations.creating
dependencies {
    railroad("de.bottlecaps.rr:rr-lib:2.6") {
        exclude(group = "org.apache.xmlgraphics")
    }
}

tasks.register<JavaExec>("grammarDiagrams") {
    group = "documentation"
    description = "Draws docs/grammar.ebnf as railroad diagrams in docs/grammar.xhtml."
    classpath = railroad
    mainClass = "de.bottlecaps.railroad.Railroad"
    val grammar = file("docs/grammar.ebnf")
    val diagrams = file("docs/grammar.xhtml")
    inputs.file(grammar)
    outputs.file(diagrams)
    // -noinline: every production gets a diagram, also those that are a single literal.
    args("-noinline", "-out:$diagrams", grammar)
}

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

// Adds the source trees beside src/main to a node's main source set: src/<version_sources>/ and, on a vanilla node,
// src/vanilla/. Each is a source set of its own only so that it goes through Stonecutter like src/main, with `//? if`
// and the rename rules; its classes compile as part of main, as the two depend on each other. Applied by every build.
val sourceSets = the<SourceSetContainer>()
val trees = listOfNotNull(property("version_sources").toString(), "vanilla".takeIf { name.endsWith("-vanilla") })

trees.forEach { tree ->
    val sources = sourceSets.create(tree)
    sourceSets["main"].java.srcDir(sources.java.sourceDirectories)
    sourceSets["main"].resources.srcDir(sources.resources.sourceDirectories)
    tasks.named(sources.compileJavaTaskName) {
        enabled = false
    }
    // Switching the active node writes its sources back to src/, but a tree isn't on every node: switching to 26.1 and
    // back to 1.21.1 would leave src/26.1/ as 26.1 had it. Every build reads the conditions as checked in instead.
    // The task's name capitalises the tree's.
    tasks.matching { it.name == "stonecutterMerge${tree.replaceFirstChar(Char::uppercase)}" }.configureEach {
        enabled = false
    }
}

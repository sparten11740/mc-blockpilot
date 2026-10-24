plugins {
    id("dev.kikugie.stonecutter")
}

// The node the IDE shows and runs; switch with the "Set active project to ..." tasks.
stonecutter active "1.21.1-neoforge"

stonecutter parameters {
    // `//? if fabric` and `//? if neoforge` in the sources, from the loader half of the node's name.
    constants.match(current.project.substringAfterLast('-'), "fabric", "neoforge")

    // Plain renames, swapped in for the versions that have them; the sources keep the names of the version they are
    // checked in for (vcsVersion in settings.gradle.kts). Switching the active version swaps them back, so each new
    // text must not appear in code both versions share. Changes beyond a rename go in `//? if` blocks instead.
    // Add one per rename as targets are added, e.g. rename("1.21.11", "ResourceLocation", "Identifier").
    @Suppress("UNUSED_VARIABLE")
    fun rename(since: String, from: String, to: String) =
        replacements.string(current.parsed >= since) { replace(from, to) }
}

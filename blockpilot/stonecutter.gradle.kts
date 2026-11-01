plugins {
    id("dev.kikugie.stonecutter")
}

// The node the IDE shows and runs; switch with the "Set active project to ..." tasks.
stonecutter active "1.21.1-neoforge"

stonecutter parameters {
    // `//? if fabric`, `//? if neoforge` and `//? if vanilla` in the sources, from the loader half of the node's name.
    constants.match(current.project.substringAfterLast('-'), "fabric", "neoforge", "vanilla")

    // Plain renames, swapped in for the versions that have them; the sources keep the names of the version they are
    // checked in for (vcsVersion in settings.gradle.kts). Switching the active version swaps them back, so each new
    // text must not appear in code both versions share. Changes beyond a rename go in `//? if` blocks instead. Each
    // rule names the earliest release known to have the new name; only 26.1 has been built against.
    fun rename(since: String, from: String, to: String) =
        replacements.string(current.parsed >= since) { replace(from, to) }

    rename("1.21.2", ".registryOrThrow(", ".lookupOrThrow(")
    rename("1.21.2", ".getHolderOrThrow(", ".getOrThrow(")
    rename("1.21.2", ".getMinBuildHeight()", ".getMinY()")
    rename("1.21.2", ".getMinSection()", ".getMinSectionY()")
    rename("1.21.2", ".getNormal()", ".getUnitVec3i()")
    // The new top is the highest block's y, the old one the height above it.
    rename("1.21.2", ".getMaxBuildHeight() - 1", ".getMaxY()")
    // With the type's context: shared code has its own isIdentifier, which a bare rule would rename back.
    rename("1.21.11", "resources.ResourceLocation;", "resources.Identifier;")
    rename("1.21.11", "ResourceLocation ", "Identifier ")
    rename("1.21.11", "ResourceLocation.", "Identifier.")
    rename("1.21.11", "dimension().location()", "dimension().identifier()")
    rename("1.21.11", "key.location()", "key.identifier()")
    rename("1.21.11", "sound.getLocation()", "sound.getIdentifier()")
    rename("26.1", "getCurrentVersion().getName()", "getCurrentVersion().name()")
    rename("26.1", ".getSharedSpawnPos()", ".getRespawnData().pos()")
    rename("26.1", "level.getDayTime()", "level.getOverworldClockTime()")
    rename("26.1", "getWindow().getWindow()", "getWindow().handle()")
    rename("26.1", "graphics.drawString(", "graphics.text(")
    rename("26.1", "camera.getYRot()", "camera.yRot()")
    rename("26.1", "camera.getXRot()", "camera.xRot()")
    rename("26.1", "camera.getPosition()", "camera.position()")
    rename("26.1", ".getItem().canAttackBlock(", ".canDestroyBlock(")
    rename("26.1", ".handleInventoryMouseClick(", ".handleContainerInput(")
    rename("26.1", "ClickType", "ContainerInput")
    rename("26.1", "client.gui.GuiGraphics;", "client.gui.GuiGraphicsExtractor;")
    rename("26.1", "GuiGraphics graphics", "GuiGraphicsExtractor graphics")
}

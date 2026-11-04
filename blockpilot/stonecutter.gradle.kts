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
    // rule names the earliest release known to have the new name; only those from 26.1 on have been built against.
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
    // 26.2 keeps the screen on Gui, and the frame's state on GameRenderer and LevelExtractor.
    rename("26.2", "minecraft.screen", "minecraft.gui.screen()")
    rename("26.2", "minecraft().screen", "minecraft().gui.screen()")
    rename("26.2", "minecraft.setScreen(", "minecraft.gui.setScreen(")
    rename("26.2", "minecraft().setScreen(", "minecraft().gui.setScreen(")
    rename("26.2", ".getOverlay()", ".gui.overlay()")
    rename("26.2", "gameRenderer.getMainCamera()", "gameRenderer.mainCamera()")
    rename("26.2", ".getMainRenderTarget()", ".gameRenderer.mainRenderTarget()")
    rename("26.2", "renderer.getSectionRenderDispatcher()", "renderer.sectionRenderDispatcher()")
    rename("26.2", "renderer.countRenderedSections()", "Minecraft.getInstance().levelExtractor.countRenderedSections()")
    rename("26.2", ".pixelSize()", ".blockSize()")
    rename("26.2", "TextureFormat.RGBA8", "GpuFormat.RGBA8_UNORM")
    rename("26.2", "pass.draw(0, 3)", "pass.draw(3, 1, 0, 0)")
    // The HUD's messages and drawing moved from Gui to the new Hud, and the mixins with them.
    rename("26.2", "client.gui.Gui;", "client.gui.Hud;")
    rename("26.2", "@Mixin(Gui.class)", "@Mixin(Hud.class)")
    rename("26.2", "RenderSection\$RebuildTask", "RenderSection\$CompileTask")
    rename("26.2", "private <T> void blockpilot\$setting(", "private static <T> void blockpilot\$setting(")
    rename("26.2", "@Inject(method = \"destroy\",", "@Inject(method = \"exitWorldAndClose\",")
    // 26.3 replaces GLFW with SDL, and moves the GPU classes to their own library.
    rename("26.3", "InputConstants.Type.KEYSYM", "InputConstants.Type.KEYBOARD")
    rename("26.3", "com.mojang.blaze3d.buffers.", "com.mojang.renderpearl.api.buffers.")
    rename("26.3", "com/mojang/blaze3d/buffers/", "com/mojang/renderpearl/api/buffers/")
    rename("26.3", "com.mojang.blaze3d.textures.", "com.mojang.renderpearl.api.textures.")
    rename("26.3", "com.mojang.blaze3d.systems.CommandEncoder;", "com.mojang.renderpearl.api.commands.CommandEncoder;")
    rename("26.3", "com.mojang.blaze3d.systems.RenderPass;", "com.mojang.renderpearl.api.commands.RenderPass;")
    rename("26.3", "com.mojang.blaze3d.systems.GpuDevice;", "com.mojang.renderpearl.api.device.GpuDevice;")
    rename("26.3", "pass.bindTexture(", "pass.setUniform(")
    rename("26.3", "pass.setPipeline(RenderPipelines.TRACY_BLIT)",
        "pass.setPipeline(RenderSystem.getCompiledPipeline(RenderPipelines.TRACY_BLIT))")
}

package dev.blockpilot.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Where a container screen draws its slots; vanilla keeps the offsets protected. */
@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {
    @Accessor("leftPos")
    int blockpilot$leftPos();

    @Accessor("topPos")
    int blockpilot$topPos();
}

package dev.blockpilot.mixin;

import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.item.CreativeModeTab;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Where the creative inventory draws a tab, relative to the screen's top left, and which tab is open. */
@Mixin(CreativeModeInventoryScreen.class)
public interface CreativeModeInventoryScreenAccessor {
    @Invoker("getTabX")
    int blockpilot$tabX(CreativeModeTab tab);

    @Invoker("getTabY")
    int blockpilot$tabY(CreativeModeTab tab);

    @Accessor("selectedTab")
    static CreativeModeTab blockpilot$selectedTab() {
        throw new AssertionError();
    }
}

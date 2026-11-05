package dev.blockpilot.mixin;

import net.minecraft.client.gui.components.AbstractSelectionList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Where a list draws its rows; vanilla keeps the row geometry protected up to 1.21.1. */
@Mixin(AbstractSelectionList.class)
public interface SelectionListAccessor {
    @Invoker("getRowTop")
    int blockpilot$rowTop(int index);

    @Invoker("getRowBottom")
    int blockpilot$rowBottom(int index);
}

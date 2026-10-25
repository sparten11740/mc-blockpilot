package dev.blockpilot.mixin;

import net.minecraft.client.gui.components.AbstractSelectionList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Where a list draws its rows; vanilla keeps the row geometry protected. */
@Mixin(AbstractSelectionList.class)
public interface SelectionListAccessor {
    @Accessor("itemHeight")
    int blockpilot$itemHeight();

    @Invoker("getRowTop")
    int blockpilot$rowTop(int index);
}

package dev.blockpilot.mixin;

import net.minecraft.client.gui.components.AbstractSelectionList;
import org.spongepowered.asm.mixin.Mixin;
//? if <1.21 {
/*import org.spongepowered.asm.mixin.gen.Accessor;
*///?}
import org.spongepowered.asm.mixin.gen.Invoker;

/** Where a list draws its rows; vanilla keeps the row geometry protected up to 1.21.1. */
@Mixin(AbstractSelectionList.class)
public interface SelectionListAccessor {
    @Invoker("getRowTop")
    int blockpilot$rowTop(int index);

    @Invoker("getRowBottom")
    int blockpilot$rowBottom(int index);

    //? if <1.21 {
    /*// The list's top and bottom on screen, which 1.20.1 keeps in fields.
    @Accessor("y0")
    int blockpilot$top();

    @Accessor("y1")
    int blockpilot$bottom();
    *///?}
}

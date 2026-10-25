package dev.blockpilot.mixin;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.WidgetTooltipHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A widget's tooltip, which 26.1 keeps in a private holder. */
@Mixin(AbstractWidget.class)
public interface AbstractWidgetAccessor {
    @Accessor("tooltip")
    WidgetTooltipHolder blockpilot$tooltip();
}

package dev.blockpilot.platform.vanilla.mixin;

import dev.blockpilot.platform.ClientPlatform;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The HUD, after everything vanilla draws on it. */
@Mixin(Gui.class)
public abstract class GuiMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void blockpilot$hud(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo info) {
        ClientPlatform.hooks().onHudRender(graphics);
    }
}

package dev.blockpilot.mixin;

import dev.blockpilot.game.observe.HudVisibility;
//? if >=1.21 {
import net.minecraft.client.DeltaTracker;
//?}
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * With clean frames, the whole HUD pass is skipped: the game's HUD and every layer and overlay other mods draw in it,
 * which F1 alone leaves to each mod. 26.1 extracts the HUD for drawing instead of drawing it, and 26.2 moved it from
 * Gui to Hud, which the rename rules write.
 */
@Mixin(Gui.class)
abstract class CleanHudMixin {
    //? if >=26.1 {
    /*@Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void blockpilot$clean(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo info) {
    *///?} else if >=1.21 {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void blockpilot$clean(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo info) {
    //?} else {
    /*@Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void blockpilot$clean(GuiGraphics graphics, float partialTick, CallbackInfo info) {
    *///?}
        if (HudVisibility.clean()) {
            info.cancel();
        }
    }
}

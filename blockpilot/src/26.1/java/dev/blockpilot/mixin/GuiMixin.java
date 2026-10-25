package dev.blockpilot.mixin;

import dev.blockpilot.game.observe.Observations;
import net.minecraft.client.gui.Gui;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Action-bar, title and subtitle messages, which never reach the chat. */
@Mixin(Gui.class)
public abstract class GuiMixin {
    @Inject(method = "setOverlayMessage", at = @At("HEAD"))
    private void blockpilot$actionBar(Component message, boolean animateColor, CallbackInfo info) {
        Observations.message("actionbar", message);
    }

    @Inject(method = "setTitle", at = @At("HEAD"))
    private void blockpilot$title(Component title, CallbackInfo info) {
        Observations.message("title", title);
    }

    @Inject(method = "setSubtitle", at = @At("HEAD"))
    private void blockpilot$subtitle(Component subtitle, CallbackInfo info) {
        Observations.message("subtitle", subtitle);
    }
}

package dev.blockpilot.mixin;

import dev.blockpilot.game.observe.HudVisibility;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Marks the client tick, whose game logic sees the player's effects while clean frames keep them out of the frame. */
@Mixin(Minecraft.class)
abstract class CleanTickMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void blockpilot$tickStarts(CallbackInfo info) {
        HudVisibility.clientTickStarted();
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void blockpilot$tickEnds(CallbackInfo info) {
        HudVisibility.clientTickEnded();
    }
}

package dev.blockpilot.mixin;

import dev.blockpilot.game.Screenshots;
import dev.blockpilot.game.perf.Perf;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Marks frame boundaries: for screenshots that need a frame rendered after their request, and for frame times. */
@Mixin(Minecraft.class)
public abstract class FrameMixin {
    @Inject(method = "runTick", at = @At("HEAD"))
    private void blockpilot$frame(boolean renderLevel, CallbackInfo info) {
        Screenshots.onFrame();
        if (Perf.active) {
            Perf.frame();
        }
    }
}

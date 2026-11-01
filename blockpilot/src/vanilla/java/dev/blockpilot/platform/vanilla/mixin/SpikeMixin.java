package dev.blockpilot.platform.vanilla.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class SpikeMixin {
    @Unique
    private static int blockpilot$frames;

    @Inject(method = "runTick", at = @At("HEAD"))
    private void blockpilot$frame(boolean renderLevel, CallbackInfo info) {
        if (++blockpilot$frames % 600 == 1) {
            System.out.println("Blockpilot agent: frame " + blockpilot$frames);
        }
    }
}

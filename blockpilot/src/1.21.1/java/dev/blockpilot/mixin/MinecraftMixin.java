package dev.blockpilot.mixin;

import dev.blockpilot.game.input.Focus;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** While an agent is connected the game acts as if its window had focus: no pause, mouse input is handled. */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Inject(method = "isWindowActive", at = @At("HEAD"), cancellable = true)
    private void blockpilot$keepActive(CallbackInfoReturnable<Boolean> info) {
        if (Focus.keepActive()) {
            info.setReturnValue(true);
        }
    }
}

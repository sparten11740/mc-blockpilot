package dev.blockpilot.mixin;

import dev.blockpilot.game.input.AimedClick;
import dev.blockpilot.game.input.Focus;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * While an agent is connected the game acts as if its window had focus: no pause, mouse input is handled. An aimed
 * click is decided between working out the crosshair's hit and handling key clicks.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Inject(method = "tick", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/GameRenderer;pick(F)V"))
    private void blockpilot$aimBeforePick(CallbackInfo info) {
        AimedClick.beforePick((Minecraft) (Object) this);
    }

    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;handleKeybinds()V"))
    private void blockpilot$aimedClick(CallbackInfo info) {
        AimedClick.beforeKeyHandling((Minecraft) (Object) this);
    }

    @Inject(method = "isWindowActive", at = @At("HEAD"), cancellable = true)
    private void blockpilot$keepActive(CallbackInfoReturnable<Boolean> info) {
        if (Focus.keepActive()) {
            info.setReturnValue(true);
        }
    }
}

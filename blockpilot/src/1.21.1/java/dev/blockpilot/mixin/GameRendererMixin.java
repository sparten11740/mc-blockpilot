package dev.blockpilot.mixin;

import dev.blockpilot.game.observe.CameraControl;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A placed camera's own field of view for the world, after the game's and a loader's, not for the hand. */
@Mixin(GameRenderer.class)
abstract class GameRendererMixin {
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void blockpilot$placedFov(Camera camera, float partialTick, boolean useFovSetting,
        CallbackInfoReturnable<Double> info) {
        Double fov = CameraControl.placedFov();
        if (useFovSetting && fov != null) {
            info.setReturnValue(fov);
        }
    }
}

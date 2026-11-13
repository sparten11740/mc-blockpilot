package dev.blockpilot.mixin;

import dev.blockpilot.game.observe.CameraControl;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
//? if <1.21 {
/*import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
*///?}

/**
 * A placed camera's own field of view for the world, after the game's and a loader's, not for the hand. On 1.20.1 also
 * its roll and the record of each frame's camera.
 */
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

    //? if <1.21 {
    /*// 1.20.1 turns the view by the camera's yaw and pitch rather than its rotation, so a placed camera's roll goes in
    // first, as Forge's own does.
    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;getXRot()F",
        ordinal = 0))
    private void blockpilot$roll(float partialTick, long finishNanos, PoseStack pose, CallbackInfo info) {
        double roll = CameraControl.placedRoll();
        if (roll != 0) {
            pose.mulPose(Axis.ZP.rotationDegrees((float) roll));
        }
    }

    // 1.20.1 draws no HUD while the GUI is hidden, as it is for a placed camera, so each frame's camera is recorded
    // here once the level is drawn.
    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void blockpilot$rendered(float partialTick, long finishNanos, PoseStack pose, CallbackInfo info) {
        CameraControl.recordRendered();
    }
    *///?}
}

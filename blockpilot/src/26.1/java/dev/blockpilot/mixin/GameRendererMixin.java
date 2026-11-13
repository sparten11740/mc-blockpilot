package dev.blockpilot.mixin;

import dev.blockpilot.game.observe.CameraControl;
import dev.blockpilot.game.observe.PixelCommands;
import net.minecraft.client.renderer.GameRenderer;
//? if >=26.1 {
/*import net.minecraft.client.renderer.state.GameRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
*///?} else {
import net.minecraft.client.Camera;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
//?}
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Records each frame's camera, so {@code pixels} can find world positions in the frame it reads, and a placed camera's
 * position and angles for {@code status} and {@code screenshot}: the HUD hook that records them on 1.21.1 doesn't run
 * while a placed camera hides the GUI. The level's projection gets view bobbing, the hurt tilt and the nausea skew only
 * here, on its way to the GPU.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    //? if >=26.1 {
    /*@Shadow
    @Final
    private GameRenderState gameRenderState;

    @ModifyArg(method = "renderLevel", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/ProjectionMatrixBuffer;getBuffer(Lorg/joml/Matrix4f;)"
            + "Lcom/mojang/blaze3d/buffers/GpuBufferSlice;"))
    private Matrix4f blockpilot$frameCamera(Matrix4f projection) {
        CameraRenderState camera = gameRenderState.levelRenderState.cameraRenderState;
        PixelCommands.recordFrame(camera.pos, camera.viewRotationMatrix, projection);
        CameraControl.recordRendered();
        return projection;
    }
    *///?} else {
    @Shadow
    @Final
    private Camera mainCamera;

    // 1.21.11's camera state has no view matrix yet: the level is drawn turned by the camera's rotation, set up by now.
    @ModifyArg(method = "renderLevel", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/PerspectiveProjectionMatrixBuffer;getBuffer(Lorg/joml/Matrix4f;)"
            + "Lcom/mojang/blaze3d/buffers/GpuBufferSlice;"))
    private Matrix4f blockpilot$frameCamera(Matrix4f projection) {
        PixelCommands.recordFrame(mainCamera.position(),
            new Matrix4f().rotation(mainCamera.rotation().conjugate(new Quaternionf())), projection);
        CameraControl.recordRendered();
        return projection;
    }

    // A placed camera's own field of view for the world, after the game's and a loader's, not for the hand; 26.1 has
    // it in the camera (CameraMixin).
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void blockpilot$placedFov(Camera camera, float partialTick, boolean useFovSetting,
        CallbackInfoReturnable<Float> info) {
        Double fov = CameraControl.placedFov();
        if (useFovSetting && fov != null) {
            info.setReturnValue(fov.floatValue());
        }
    }
    //?}
}

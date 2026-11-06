package dev.blockpilot.mixin;

import dev.blockpilot.game.observe.CameraControl;
import dev.blockpilot.game.observe.PixelCommands;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.GameRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
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
    @Shadow
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
}

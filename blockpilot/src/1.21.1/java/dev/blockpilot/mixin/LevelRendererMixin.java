package dev.blockpilot.mixin;

import dev.blockpilot.game.observe.PixelCommands;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Records each frame's camera, so {@code pixels} can find world positions in the frame it reads. */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void blockpilot$frameCamera(DeltaTracker deltaTracker, boolean renderBlockOutline, Camera camera,
        GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f frustumMatrix, Matrix4f projectionMatrix,
        CallbackInfo info) {
        PixelCommands.recordFrame(camera.getPosition(), frustumMatrix, projectionMatrix);
    }
}

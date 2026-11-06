package dev.blockpilot.mixin;

import dev.blockpilot.game.observe.AwayCamera;
import dev.blockpilot.game.observe.PixelCommands;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Records each frame's camera, so {@code pixels} can find world positions in the frame it reads. While a camera is
 * away from the player, the sections drawn centre on it: 1.21.1 centres them on the player, which 26.x doesn't.
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void blockpilot$frameCamera(DeltaTracker deltaTracker, boolean renderBlockOutline, Camera camera,
        GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f frustumMatrix, Matrix4f projectionMatrix,
        CallbackInfo info) {
        PixelCommands.recordFrame(camera.getPosition(), frustumMatrix, projectionMatrix);
    }

    @Redirect(method = "setupRender", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/player/LocalPlayer;getX()D"))
    private double blockpilot$centreX(LocalPlayer player) {
        return AwayCamera.active() ? camera().x : player.getX();
    }

    @Redirect(method = "setupRender", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/player/LocalPlayer;getY()D"))
    private double blockpilot$centreY(LocalPlayer player) {
        return AwayCamera.active() ? camera().y : player.getY();
    }

    @Redirect(method = "setupRender", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/player/LocalPlayer;getZ()D"))
    private double blockpilot$centreZ(LocalPlayer player) {
        return AwayCamera.active() ? camera().z : player.getZ();
    }

    private static Vec3 camera() {
        return Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
    }
}

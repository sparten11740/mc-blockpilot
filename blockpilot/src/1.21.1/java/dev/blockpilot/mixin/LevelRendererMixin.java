package dev.blockpilot.mixin;

import dev.blockpilot.game.observe.AwayCamera;
import dev.blockpilot.game.observe.PixelCommands;
import net.minecraft.client.Camera;
//? if >=1.21 {
import net.minecraft.client.DeltaTracker;
//?} else {
/*import com.mojang.blaze3d.vertex.PoseStack;
*///?}
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
 *
 * <p>Renderers such as Sodium overwrite {@code setupRender} with their own, which centres on the camera already. Mixin
 * refuses to inject into a method another mixin overwrote at the same or a higher priority, so this one sits above the
 * default 1000, and its redirects may find nothing to redirect there.
 */
@Mixin(value = LevelRenderer.class, priority = 1100)
public abstract class LevelRendererMixin {
    //? if >=1.21 {
    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void blockpilot$frameCamera(DeltaTracker deltaTracker, boolean renderBlockOutline, Camera camera,
        GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f frustumMatrix, Matrix4f projectionMatrix,
        CallbackInfo info) {
        PixelCommands.recordFrame(camera.getPosition(), frustumMatrix, projectionMatrix);
    }
    //?} else {
    /*// 1.20.1 hands over the camera's turn as the pose, where 1.21.1 hands it over as the frustum matrix.
    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void blockpilot$frameCamera(PoseStack pose, float partialTick, long finishNanos, boolean renderBlockOutline,
        Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f projectionMatrix,
        CallbackInfo info) {
        PixelCommands.recordFrame(camera.getPosition(), new Matrix4f(pose.last().pose()), projectionMatrix);
    }
    *///?}

    @Redirect(method = "setupRender", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/player/LocalPlayer;getX()D"), require = 0)
    private double blockpilot$centreX(LocalPlayer player) {
        return AwayCamera.active() ? camera().x : player.getX();
    }

    @Redirect(method = "setupRender", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/player/LocalPlayer;getY()D"), require = 0)
    private double blockpilot$centreY(LocalPlayer player) {
        return AwayCamera.active() ? camera().y : player.getY();
    }

    @Redirect(method = "setupRender", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/player/LocalPlayer;getZ()D"), require = 0)
    private double blockpilot$centreZ(LocalPlayer player) {
        return AwayCamera.active() ? camera().z : player.getZ();
    }

    private static Vec3 camera() {
        return Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
    }
}

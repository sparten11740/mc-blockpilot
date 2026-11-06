package dev.blockpilot.mixin;

import dev.blockpilot.game.observe.CameraControl;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Renders from a placed camera when an agent set one, with the player drawn like in third person. The camera is
 * moved right after it follows its entity, before the culling frustum is worked out from where it is.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    private boolean detached;

    @Shadow
    @Final
    private Quaternionf rotation;

    @Shadow
    protected abstract void setPosition(double x, double y, double z);

    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Shadow
    public abstract float getCameraEntityPartialTicks(DeltaTracker deltaTracker);

    @Inject(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;alignWithEntity(F)V",
        shift = At.Shift.AFTER))
    private void blockpilot$placed(DeltaTracker deltaTracker, CallbackInfo info) {
        CameraControl.Placement placement = CameraControl.placement();
        if (placement == null) {
            return;
        }
        float partialTick = getCameraEntityPartialTicks(deltaTracker);
        Vec3 at = placement.position().at(partialTick);
        setPosition(at.x, at.y, at.z);
        float[] angles = placement.angles(partialTick);
        setRotation(angles[0], angles[1]);
        CameraControl.roll(rotation);
        detached = true;
    }

    /** A placed camera's own field of view, in place of the player's and its effects. */
    @Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
    private void blockpilot$placedFov(float partialTick, CallbackInfoReturnable<Float> info) {
        Double fov = CameraControl.placedFov();
        if (fov != null) {
            info.setReturnValue(fov.floatValue());
        }
    }
}

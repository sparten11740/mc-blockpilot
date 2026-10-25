package dev.blockpilot.mixin;

import dev.blockpilot.game.observe.CameraControl;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Renders from a placed camera when an agent set one, with the player drawn like in third person. */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    private boolean detached;

    @Shadow
    protected abstract void setPosition(double x, double y, double z);

    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Inject(method = "setup", at = @At("TAIL"))
    private void blockpilot$placed(BlockGetter level, Entity entity, boolean thirdPerson, boolean thirdPersonReverse,
        float partialTick, CallbackInfo info) {
        CameraControl.Placement placement = CameraControl.placement();
        if (placement == null) {
            return;
        }
        Vec3 at = placement.position().at(partialTick);
        setPosition(at.x, at.y, at.z);
        float[] angles = placement.angles(partialTick);
        setRotation(angles[0], angles[1]);
        detached = true;
    }
}

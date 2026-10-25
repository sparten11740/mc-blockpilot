package dev.blockpilot.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.blockpilot.game.Ids;
import dev.blockpilot.game.perf.Perf;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Times entity renderers (CPU side) per entity type during a performance capture. */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void blockpilot$start(Entity entity, double x, double y, double z, float rotationYaw, float partialTicks,
        PoseStack poseStack, MultiBufferSource buffer, int packedLight, CallbackInfo info) {
        if (Perf.active) {
            Perf.entityRenderStart();
        }
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void blockpilot$end(Entity entity, double x, double y, double z, float rotationYaw, float partialTicks,
        PoseStack poseStack, MultiBufferSource buffer, int packedLight, CallbackInfo info) {
        if (Perf.active) {
            Perf.entityRenderEnd(Ids.of(entity));
        }
    }
}

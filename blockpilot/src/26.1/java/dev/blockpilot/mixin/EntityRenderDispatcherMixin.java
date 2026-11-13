package dev.blockpilot.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.blockpilot.game.common.Ids;
import dev.blockpilot.game.perf.Perf;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Times entity renderers (CPU side) per entity type during a performance capture: extracting each entity's render
 * state and submitting it, the two steps a renderer's own code runs in from 26.1; the submit completes the render.
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
    @Inject(method = "extractEntity", at = @At("HEAD"))
    private void blockpilot$extractStart(Entity entity, float partialTicks,
        CallbackInfoReturnable<EntityRenderState> info) {
        if (Perf.active) {
            Perf.entityRenderStart();
        }
    }

    @Inject(method = "extractEntity", at = @At("RETURN"))
    private void blockpilot$extractEnd(Entity entity, float partialTicks,
        CallbackInfoReturnable<EntityRenderState> info) {
        if (Perf.active) {
            Perf.entityRenderStepEnd(Ids.of(entity));
        }
    }

    @Inject(method = "submit", at = @At("HEAD"))
    private void blockpilot$submitStart(EntityRenderState state, CameraRenderState camera, double x, double y, double z,
        PoseStack poseStack, SubmitNodeCollector collector, CallbackInfo info) {
        if (Perf.active) {
            Perf.entityRenderStart();
        }
    }

    @Inject(method = "submit", at = @At("RETURN"))
    private void blockpilot$submitEnd(EntityRenderState state, CameraRenderState camera, double x, double y, double z,
        PoseStack poseStack, SubmitNodeCollector collector, CallbackInfo info) {
        if (Perf.active) {
            Perf.entityRenderEnd(Ids.of(state.entityType));
        }
    }
}

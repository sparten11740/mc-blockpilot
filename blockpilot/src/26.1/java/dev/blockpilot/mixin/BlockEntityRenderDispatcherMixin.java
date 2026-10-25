package dev.blockpilot.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.blockpilot.game.Ids;
import dev.blockpilot.game.perf.Perf;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Times block-entity renderers (CPU side) per block-entity type during a performance capture. From 26.1 a renderer's
 * own code runs in two steps, extracting its render state and submitting it; both count as its render, which the
 * submit completes. The drawing after that is batched for all of them.
 */
@Mixin(BlockEntityRenderDispatcher.class)
public abstract class BlockEntityRenderDispatcherMixin {
    /**
     * Wraps the renderer's own call rather than the extraction overloads: NeoForge adds one that vanilla's calls, and
     * timing both would count the time twice.
     */
    @WrapOperation(method = "tryExtractRenderState*", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/blockentity/BlockEntityRenderer;extractRenderState("
            + "Lnet/minecraft/world/level/block/entity/BlockEntity;"
            + "Lnet/minecraft/client/renderer/blockentity/state/BlockEntityRenderState;F"
            + "Lnet/minecraft/world/phys/Vec3;"
            + "Lnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V"))
    private void blockpilot$extract(BlockEntityRenderer<BlockEntity, BlockEntityRenderState> renderer,
        BlockEntity blockEntity, BlockEntityRenderState state, float partialTicks, Vec3 cameraPosition,
        ModelFeatureRenderer.CrumblingOverlay breakProgress, Operation<Void> original) {
        if (!Perf.active) {
            original.call(renderer, blockEntity, state, partialTicks, cameraPosition, breakProgress);
            return;
        }
        Perf.renderStart();
        original.call(renderer, blockEntity, state, partialTicks, cameraPosition, breakProgress);
        Perf.renderStepEnd(Ids.of(blockEntity.getType()));
    }

    @Inject(method = "submit", at = @At("HEAD"))
    private void blockpilot$submitStart(BlockEntityRenderState state, PoseStack poseStack,
        SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo info) {
        if (Perf.active) {
            Perf.renderStart();
        }
    }

    @Inject(method = "submit", at = @At("RETURN"))
    private void blockpilot$submitEnd(BlockEntityRenderState state, PoseStack poseStack,
        SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo info) {
        if (Perf.active) {
            Perf.renderEnd(Ids.of(state.blockEntityType));
        }
    }
}

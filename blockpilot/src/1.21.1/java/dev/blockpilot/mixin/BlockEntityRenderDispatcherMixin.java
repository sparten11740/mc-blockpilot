package dev.blockpilot.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.blockpilot.game.Ids;
import dev.blockpilot.game.perf.Perf;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Times block-entity renderers (CPU side) per block-entity type during a performance capture. */
@Mixin(BlockEntityRenderDispatcher.class)
public abstract class BlockEntityRenderDispatcherMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void blockpilot$start(BlockEntity blockEntity, float partialTick, PoseStack poseStack,
        MultiBufferSource buffers, CallbackInfo info) {
        if (Perf.active) {
            Perf.renderStart();
        }
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void blockpilot$end(BlockEntity blockEntity, float partialTick, PoseStack poseStack,
        MultiBufferSource buffers, CallbackInfo info) {
        if (Perf.active) {
            Perf.renderEnd(Ids.of(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity.getType())));
        }
    }
}

package dev.blockpilot.mixin;

import dev.blockpilot.game.Ids;
import dev.blockpilot.game.perf.Perf;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Times each block entity's ticker during a performance capture, per block-entity type and side. */
@Mixin(targets = "net.minecraft.world.level.chunk.LevelChunk$BoundTickingBlockEntity")
public abstract class BoundTickingBlockEntityMixin {
    @Shadow
    @Final
    private BlockEntity blockEntity;

    @Inject(method = "tick", at = @At("HEAD"))
    private void blockpilot$start(CallbackInfo info) {
        if (Perf.active) {
            Perf.blockEntityStart(blockEntity.getLevel() != null && blockEntity.getLevel().isClientSide());
        }
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void blockpilot$end(CallbackInfo info) {
        if (Perf.active) {
            Perf.blockEntityEnd(blockEntity.getLevel() != null && blockEntity.getLevel().isClientSide(),
                Ids.of(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity.getType())));
        }
    }
}

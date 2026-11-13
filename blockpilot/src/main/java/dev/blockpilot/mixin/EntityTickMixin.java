package dev.blockpilot.mixin;

import dev.blockpilot.game.common.Ids;
import dev.blockpilot.game.perf.Perf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Times each top-level server entity tick (passengers included) during a performance capture, per entity type. One
 * mixin a level, as Forge's refmap names each level's method apart.
 */
@Mixin(ServerLevel.class)
public abstract class EntityTickMixin {
    @Inject(method = "tickNonPassenger", at = @At("HEAD"))
    private void blockpilot$start(Entity entity, CallbackInfo info) {
        if (Perf.active) {
            Perf.entityStart(entity.level().isClientSide());
        }
    }

    @Inject(method = "tickNonPassenger", at = @At("RETURN"))
    private void blockpilot$end(Entity entity, CallbackInfo info) {
        if (Perf.active) {
            Perf.entityEnd(entity.level().isClientSide(), Ids.of(entity));
        }
    }
}

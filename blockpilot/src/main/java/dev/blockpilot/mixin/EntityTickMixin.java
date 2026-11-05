package dev.blockpilot.mixin;

import dev.blockpilot.game.Ids;
import dev.blockpilot.game.perf.Perf;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Times each top-level entity tick (passengers included) during a performance capture, per entity type. */
@Mixin({ServerLevel.class, ClientLevel.class})
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

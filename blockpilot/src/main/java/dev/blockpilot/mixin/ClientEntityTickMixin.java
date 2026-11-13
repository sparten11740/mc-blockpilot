package dev.blockpilot.mixin;

import dev.blockpilot.game.common.Ids;
import dev.blockpilot.game.perf.Perf;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Times each top-level client entity tick during a performance capture, as EntityTickMixin does the server's. */
@Mixin(ClientLevel.class)
public abstract class ClientEntityTickMixin {
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

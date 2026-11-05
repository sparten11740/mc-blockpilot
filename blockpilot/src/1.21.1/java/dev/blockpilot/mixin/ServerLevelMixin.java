package dev.blockpilot.mixin;

import dev.blockpilot.game.common.Ids;
import dev.blockpilot.game.perf.ParticleSources;
import dev.blockpilot.game.perf.Perf;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Traces, during a performance capture, a sample of the particles server code sends to the code that sent them. Only
 * the overloads that build the packet are hooked, so a particle that passes through two of them counts once.
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {
    @Inject(method = "sendParticles(Lnet/minecraft/core/particles/ParticleOptions;DDDIDDDD)I", at = @At("HEAD"))
    private void blockpilot$sent(ParticleOptions type, double x, double y, double z, int count, double xDist,
        double yDist, double zDist, double speed, CallbackInfoReturnable<Integer> info) {
        traced(type);
    }

    @Inject(method = "sendParticles(Lnet/minecraft/server/level/ServerPlayer;"
        + "Lnet/minecraft/core/particles/ParticleOptions;ZDDDIDDDD)Z", at = @At("HEAD"))
    private void blockpilot$sentTo(ServerPlayer player, ParticleOptions type, boolean longDistance, double x,
        double y, double z, int count, double xDist, double yDist, double zDist, double speed,
        CallbackInfoReturnable<Boolean> info) {
        traced(type);
    }

    private static void traced(ParticleOptions type) {
        if (Perf.active) {
            ParticleSources.sent(Ids.of(BuiltInRegistries.PARTICLE_TYPE.getKey(type.getType())));
        }
    }
}

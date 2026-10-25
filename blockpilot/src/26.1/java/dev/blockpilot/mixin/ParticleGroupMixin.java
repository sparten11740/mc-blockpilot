package dev.blockpilot.mixin;

import dev.blockpilot.game.perf.ParticleTag;
import dev.blockpilot.game.perf.Perf;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleGroup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Times particle ticks per type during a performance capture; every group ticks its particles here. */
@Mixin(ParticleGroup.class)
public abstract class ParticleGroupMixin {
    @Inject(method = "tickParticle", at = @At("HEAD"))
    private void blockpilot$tickStart(Particle particle, CallbackInfo info) {
        if (Perf.active) {
            Perf.particleTickStart();
        }
    }

    @Inject(method = "tickParticle", at = @At("RETURN"))
    private void blockpilot$tickEnd(Particle particle, CallbackInfo info) {
        if (Perf.active) {
            Perf.particleTickEnd(ParticleTag.of(particle));
        }
    }
}

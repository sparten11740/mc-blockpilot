package dev.blockpilot.mixin;

import dev.blockpilot.game.Ids;
import dev.blockpilot.game.observe.Observations;
import dev.blockpilot.game.perf.ParticleSources;
import dev.blockpilot.game.perf.ParticleTag;
import dev.blockpilot.game.perf.Perf;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Counts particles by type as the client creates them (the path level particles and mods' spawns take), tags each
 * with its type, and during a performance capture traces a sample to the code that spawned them. Their ticks and
 * renders are timed in their groups (ParticleGroupMixin, QuadParticleGroupMixin).
 */
@Mixin(ParticleEngine.class)
public abstract class ParticleEngineMixin {
    @Inject(method = "createParticle", at = @At("RETURN"))
    private void blockpilot$created(ParticleOptions options, double x, double y, double z, double xSpeed,
        double ySpeed, double zSpeed, CallbackInfoReturnable<Particle> info) {
        if (info.getReturnValue() != null) {
            Observations.particle(options.getType());
            String type = Ids.of(BuiltInRegistries.PARTICLE_TYPE.getKey(options.getType()));
            ((ParticleTag) info.getReturnValue()).blockpilot$type(type);
            if (Perf.active) {
                ParticleSources.created(type);
            }
        }
    }
}

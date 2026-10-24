package dev.blockpilot.mixin;

import dev.blockpilot.game.observe.Observations;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Counts particles by type as the client creates them (the path level particles and mods' spawns take). */
@Mixin(ParticleEngine.class)
public abstract class ParticleEngineMixin {
    @Inject(method = "createParticle", at = @At("RETURN"))
    private void blockpilot$created(ParticleOptions options, double x, double y, double z, double xSpeed,
        double ySpeed, double zSpeed, CallbackInfoReturnable<Particle> info) {
        if (info.getReturnValue() != null) {
            Observations.particle(options.getType());
        }
    }
}

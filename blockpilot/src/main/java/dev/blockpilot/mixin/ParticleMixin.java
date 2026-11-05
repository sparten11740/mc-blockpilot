package dev.blockpilot.mixin;

import dev.blockpilot.game.perf.ParticleTag;
import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Gives every particle a type tag for the performance capture's per-type timings and counts. */
@Mixin(Particle.class)
public abstract class ParticleMixin implements ParticleTag {
    @Unique
    private String blockpilot$type;

    @Override
    public String blockpilot$type() {
        return blockpilot$type;
    }

    @Override
    public void blockpilot$type(String type) {
        blockpilot$type = type;
    }
}

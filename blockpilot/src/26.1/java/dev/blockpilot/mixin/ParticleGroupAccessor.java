package dev.blockpilot.mixin;

import java.util.Queue;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleGroup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A group's live particles, which vanilla keeps protected and from 26.2 offers no other way to read. */
@Mixin(ParticleGroup.class)
public interface ParticleGroupAccessor {
    @Accessor("particles")
    Queue<Particle> blockpilot$particles();
}

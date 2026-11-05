package dev.blockpilot.mixin;

import java.util.Map;
import net.minecraft.client.particle.ParticleEngine;
//? if >=26.1 {
/*import net.minecraft.client.particle.ParticleGroup;
*///?} else {
import java.util.Queue;
import net.minecraft.client.particle.Particle;
//?}
import net.minecraft.client.particle.ParticleRenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The live particles, for the counts a performance capture reports when it stops. */
@Mixin(ParticleEngine.class)
public interface ParticleEngineAccessor {
    @Accessor("particles")
    //? if >=26.1 {
    /*Map<ParticleRenderType, ParticleGroup<?>> blockpilot$particles();
    *///?} else {
    Map<ParticleRenderType, Queue<Particle>> blockpilot$particles();
    //?}
}

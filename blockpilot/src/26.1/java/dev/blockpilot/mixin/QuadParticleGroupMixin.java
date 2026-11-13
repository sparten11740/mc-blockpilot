package dev.blockpilot.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.blockpilot.game.perf.ParticleTag;
import dev.blockpilot.game.perf.Perf;
import net.minecraft.client.Camera;
import net.minecraft.client.particle.QuadParticleGroup;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.state.QuadParticleRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Times particle rendering per type during a performance capture. From 26.1 a particle's own render code runs when
 * its group extracts it into the frame's render state; the drawing after that is batched for all particles. Quad
 * particles, nearly all of them, are timed; groups that draw particles their own way are not.
 */
@Mixin(QuadParticleGroup.class)
public abstract class QuadParticleGroupMixin {
    @WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/particle/SingleQuadParticle;extract("
            + "Lnet/minecraft/client/renderer/state/QuadParticleRenderState;Lnet/minecraft/client/Camera;F)V"))
    private void blockpilot$extract(SingleQuadParticle particle, QuadParticleRenderState state, Camera camera,
        float partialTick, Operation<Void> original) {
        if (!Perf.active) {
            original.call(particle, state, camera, partialTick);
            return;
        }
        long start = System.nanoTime();
        original.call(particle, state, camera, partialTick);
        Perf.particleRendered(ParticleTag.of(particle), System.nanoTime() - start);
    }
}

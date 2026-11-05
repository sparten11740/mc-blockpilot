package dev.blockpilot.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.blockpilot.game.common.Ids;
import dev.blockpilot.game.observe.Observations;
import dev.blockpilot.game.perf.ParticleSources;
import dev.blockpilot.game.perf.ParticleTag;
import dev.blockpilot.game.perf.Perf;
import net.minecraft.client.Camera;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Counts particles by type as the client creates them (the path level particles and mods' spawns take), tags each
 * with its type, and during a performance capture times their ticks and renders per type and traces a sample to the
 * code that spawned them.
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

    /**
     * Only the call site knows which particle renders, so the call is wrapped (which composes with other mods). The
     * loop is in vanilla's render and in the overload NeoForge adds; each loader has the call in one of them.
     */
    @WrapOperation(method = {
        "render(Lnet/minecraft/client/renderer/LightTexture;Lnet/minecraft/client/Camera;F)V",
        "render(Lnet/minecraft/client/renderer/LightTexture;Lnet/minecraft/client/Camera;F"
            + "Lnet/minecraft/client/renderer/culling/Frustum;Ljava/util/function/Predicate;)V"},
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/particle/Particle;"
            + "render(Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/client/Camera;F)V"))
    private void blockpilot$render(Particle particle, VertexConsumer consumer, Camera camera, float partialTick,
        Operation<Void> original) {
        if (!Perf.active) {
            original.call(particle, consumer, camera, partialTick);
            return;
        }
        long start = System.nanoTime();
        original.call(particle, consumer, camera, partialTick);
        Perf.particleRendered(ParticleTag.of(particle), System.nanoTime() - start);
    }
}

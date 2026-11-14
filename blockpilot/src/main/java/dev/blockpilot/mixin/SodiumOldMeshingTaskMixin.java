package dev.blockpilot.mixin;

import dev.blockpilot.game.perf.Perf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Counts the section rebuilds of Sodium 0.5 and Embeddium, on 1.20.1, whose tasks keep Sodium's old package. As
 * {@link SodiumMeshingTaskMixin}, skipped without them.
 */
@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.render.chunk.compile.tasks.ChunkBuilderMeshingTask", remap = false)
public abstract class SodiumOldMeshingTaskMixin {
    @Inject(method = "execute(Lme/jellysquid/mods/sodium/client/render/chunk/compile/ChunkBuildContext;"
        + "Lme/jellysquid/mods/sodium/client/util/task/CancellationToken;)"
        + "Lme/jellysquid/mods/sodium/client/render/chunk/compile/ChunkBuildOutput;", at = @At("HEAD"), require = 0)
    private void blockpilot$rebuilt(CallbackInfoReturnable<?> info) {
        if (Perf.active) {
            Perf.sectionRebuilt();
        }
    }
}

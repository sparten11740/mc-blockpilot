package dev.blockpilot.mixin;

import dev.blockpilot.game.perf.Perf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Counts the section rebuilds of Sodium 0.6 and later, which meshes sections with tasks of its own instead of the
 * game's. Skipped without Sodium; a Sodium whose task changed loses only the count.
 */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.compile.tasks.ChunkBuilderMeshingTask", remap = false)
public abstract class SodiumMeshingTaskMixin {
    // By its descriptor, so as not to count again in the bridge method of the same name, which calls this one.
    @Inject(method = "execute(Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/ChunkBuildContext;"
        + "Lnet/caffeinemc/mods/sodium/client/util/task/CancellationToken;)"
        + "Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/ChunkBuildOutput;", at = @At("HEAD"), require = 0)
    private void blockpilot$rebuilt(CallbackInfoReturnable<?> info) {
        if (Perf.active) {
            Perf.sectionRebuilt();
        }
    }
}

package dev.blockpilot.mixin;

import dev.blockpilot.game.perf.Perf;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Counts chunk section rebuilds (re-meshing) during a performance capture. */
@Mixin(targets = "net.minecraft.client.renderer.chunk.SectionRenderDispatcher$RenderSection$RebuildTask")
public abstract class RebuildTaskMixin {
    @Inject(method = "doTask", at = @At("HEAD"))
    private void blockpilot$rebuilt(SectionBufferBuilderPack buffers, CallbackInfoReturnable<?> info) {
        if (Perf.active) {
            Perf.sectionRebuilt();
        }
    }
}

package dev.blockpilot.mixin;

import dev.blockpilot.game.perf.BlockChanges;
import dev.blockpilot.game.perf.Perf;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Counts, during a performance capture, the block changes a level marks for sending to clients: every call, since a
 * Sable plot's changes reach clients without a vanilla chunk holder.
 */
@Mixin(ServerChunkCache.class)
public abstract class ServerChunkCacheMixin {
    @Shadow
    @Final
    public ServerLevel level;

    @Inject(method = "blockChanged", at = @At("HEAD"))
    private void blockpilot$changed(BlockPos pos, CallbackInfo info) {
        if (Perf.active) {
            BlockChanges.changed(level, pos);
        }
    }
}

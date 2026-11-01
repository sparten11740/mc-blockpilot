package dev.blockpilot.platform.vanilla.mixin;

import dev.blockpilot.platform.ClientPlatform;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Chunks becoming full in a server level. Only the server marks a chunk loaded, once it is full, and only generation
 * builds one from a proto-chunk; one read from disk comes ready-made.
 */
@Mixin(LevelChunk.class)
public abstract class LevelChunkMixin {
    @Unique
    private boolean blockpilot$generated;

    @Inject(method = "<init>(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ProtoChunk;"
        + "Lnet/minecraft/world/level/chunk/LevelChunk$PostLoadProcessor;)V", at = @At("TAIL"))
    private void blockpilot$generated(ServerLevel level, ProtoChunk protoChunk, LevelChunk.PostLoadProcessor postLoad,
        CallbackInfo info) {
        blockpilot$generated = true;
    }

    @Inject(method = "setLoaded", at = @At("HEAD"))
    private void blockpilot$loaded(boolean loaded, CallbackInfo info) {
        if (loaded) {
            ClientPlatform.hooks().onServerChunkLoaded();
            if (blockpilot$generated) {
                ClientPlatform.hooks().onServerChunkGenerated();
            }
        }
    }
}

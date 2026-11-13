package dev.blockpilot.mixin;

import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
//? if <1.21 {
/*import dev.blockpilot.platform.ClientPlatform;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ProtoChunk;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
*///?}

/**
 * Reports a server chunk that became full from a proto chunk, which is one that was generated, on 1.20.1, where Fabric
 * API has no event for it; a chunk loaded full from its save is built another way. Both loaders take it from here on
 * 1.20.1. Empty on later versions.
 */
@Mixin(LevelChunk.class)
public abstract class NewChunkMixin {
    //? if <1.21 {
    /*@Inject(method = "<init>(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ProtoChunk;"
        + "Lnet/minecraft/world/level/chunk/LevelChunk$PostLoadProcessor;)V", at = @At("RETURN"))
    private void blockpilot$generated(ServerLevel level, ProtoChunk chunk, LevelChunk.PostLoadProcessor postLoad,
        CallbackInfo info) {
        ClientPlatform.hooks().onServerChunkGenerated();
    }
    *///?}
}

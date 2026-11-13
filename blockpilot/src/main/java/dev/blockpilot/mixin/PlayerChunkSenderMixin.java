package dev.blockpilot.mixin;

import dev.blockpilot.game.perf.Perf;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if >=1.21 {
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.PlayerChunkSender;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
//?} else {
/*import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import org.apache.commons.lang3.mutable.MutableObject;
*///?}

/**
 * Counts, during a performance capture, the chunks the server sends to players, per server tick. 1.20.1 has no chunk
 * sender: its chunk map sends each chunk as a player comes in range of it.
 */
//? if >=1.21 {
@Mixin(PlayerChunkSender.class)
//?} else {
/*@Mixin(ChunkMap.class)
*///?}
public abstract class PlayerChunkSenderMixin {
    //? if >=1.21 {
    @Inject(method = "sendChunk", at = @At("HEAD"))
    private static void blockpilot$sent(ServerGamePacketListenerImpl connection, ServerLevel level, LevelChunk chunk,
        CallbackInfo info) {
        Perf.chunkSent();
    }
    //?} else {
    /*@Inject(method = "playerLoadedChunk", at = @At("HEAD"))
    private void blockpilot$sent(ServerPlayer player, MutableObject<ClientboundLevelChunkWithLightPacket> packet,
        LevelChunk chunk, CallbackInfo info) {
        Perf.chunkSent();
    }
    *///?}
}

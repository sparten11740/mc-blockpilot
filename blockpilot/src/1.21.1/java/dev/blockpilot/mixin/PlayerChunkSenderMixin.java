package dev.blockpilot.mixin;

import dev.blockpilot.game.perf.Perf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.PlayerChunkSender;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Counts, during a performance capture, the chunks the server sends to players, per server tick. */
@Mixin(PlayerChunkSender.class)
public abstract class PlayerChunkSenderMixin {
    @Inject(method = "sendChunk", at = @At("HEAD"))
    private static void blockpilot$sent(ServerGamePacketListenerImpl connection, ServerLevel level, LevelChunk chunk,
        CallbackInfo info) {
        Perf.chunkSent();
    }
}

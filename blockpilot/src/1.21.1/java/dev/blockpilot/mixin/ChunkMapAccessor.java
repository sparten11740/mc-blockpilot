package dev.blockpilot.mixin;

import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The server's loaded chunks, to count loaded block entities at the end of a performance capture. */
@Mixin(ChunkMap.class)
public interface ChunkMapAccessor {
    @Invoker("getChunks")
    Iterable<ChunkHolder> blockpilot$chunks();
}

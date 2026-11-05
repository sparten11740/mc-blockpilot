package dev.blockpilot.mixin;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The server's loaded chunks, to count loaded block entities at the end of a performance capture. */
@Mixin(ChunkMap.class)
public interface ChunkMapAccessor {
    @Accessor("visibleChunkMap")
    Long2ObjectLinkedOpenHashMap<ChunkHolder> blockpilot$visibleChunks();
}

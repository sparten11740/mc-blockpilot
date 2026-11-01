package dev.blockpilot.game;

import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

/** Visits matching blocks in loaded chunks, skipping sections whose palette can't match; never loads a chunk. */
public final class BlockScan {
    /** The most matches find and sable:find list. */
    public static final int MAX_FIND_LIMIT = 1000;

    private BlockScan() {
    }

    /** Within the cube around the centre. */
    public static void around(Level level, BlockPos center, int radius, BlockMatcher matcher,
        boolean withBlockEntities, BiConsumer<BlockPos, BlockState> visitor) {
        box(level, center.offset(-radius, -radius, -radius), center.offset(radius, radius, radius), matcher,
            withBlockEntities, visitor);
    }

    /**
     * Within the box, corners included. With block entities, those that don't match are visited too (look lists them
     * as notable whatever they are).
     */
    public static void box(Level level, BlockPos min, BlockPos max, BlockMatcher matcher, boolean withBlockEntities,
        BiConsumer<BlockPos, BlockState> visitor) {
        int minX = min.getX();
        int maxX = max.getX();
        int minY = Math.max(level.getMinBuildHeight(), min.getY());
        int maxY = Math.min(level.getMaxBuildHeight() - 1, max.getY());
        int minZ = min.getZ();
        int maxZ = max.getZ();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int chunkX = minX >> 4; chunkX <= maxX >> 4; chunkX++) {
            for (int chunkZ = minZ >> 4; chunkZ <= maxZ >> 4; chunkZ++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk == null) {
                    continue;
                }
                if (withBlockEntities) {
                    for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                        BlockPos pos = blockEntity.getBlockPos();
                        if (inside(pos, minX, maxX, minY, maxY, minZ, maxZ)
                            && !matcher.test(blockEntity.getBlockState())) {
                            visitor.accept(pos, blockEntity.getBlockState());
                        }
                    }
                }
                LevelChunkSection[] sections = chunk.getSections();
                for (int index = 0; index < sections.length; index++) {
                    LevelChunkSection section = sections[index];
                    int sectionY = (chunk.getMinSection() + index) << 4;
                    if (section.hasOnlyAir() || sectionY + 15 < minY || sectionY > maxY
                        || !section.getStates().maybeHas(matcher)) {
                        continue;
                    }
                    for (int y = Math.max(minY, sectionY); y <= Math.min(maxY, sectionY + 15); y++) {
                        for (int x = Math.max(minX, chunkX << 4); x <= Math.min(maxX, (chunkX << 4) + 15); x++) {
                            for (int z = Math.max(minZ, chunkZ << 4); z <= Math.min(maxZ, (chunkZ << 4) + 15); z++) {
                                BlockState state = section.getBlockState(x & 15, y & 15, z & 15);
                                if (matcher.test(state)) {
                                    visitor.accept(cursor.set(x, y, z), state);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private static boolean inside(BlockPos pos, int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
        return pos.getX() >= minX && pos.getX() <= maxX && pos.getY() >= minY && pos.getY() <= maxY
            && pos.getZ() >= minZ && pos.getZ() <= maxZ;
    }
}

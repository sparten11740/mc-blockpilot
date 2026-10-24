package dev.blockpilot.game.path;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathComputationType;

/** The pathfinder's view of a loaded level. */
public final class WorldTerrain implements Terrain {
    private final Level level;
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

    public WorldTerrain(Level level) {
        this.level = level;
    }

    private BlockState state(int x, int y, int z) {
        return level.getBlockState(cursor.set(x, y, z));
    }

    @Override
    public boolean loaded(int x, int y, int z) {
        return level.getChunkSource().getChunkNow(x >> 4, z >> 4) != null && !level.isOutsideBuildHeight(y);
    }

    @Override
    public boolean solid(int x, int y, int z) {
        BlockState state = state(x, y, z);
        return !state.getCollisionShape(level, cursor).isEmpty() && !state.is(BlockTags.FENCES)
            && !state.is(BlockTags.WALLS) && !state.is(BlockTags.FENCE_GATES);
    }

    @Override
    public boolean passable(int x, int y, int z) {
        BlockState state = state(x, y, z);
        return state.isPathfindable(PathComputationType.LAND) && !state.getFluidState().is(FluidTags.LAVA);
    }

    @Override
    public boolean water(int x, int y, int z) {
        return state(x, y, z).getFluidState().is(FluidTags.WATER);
    }

    @Override
    public boolean climbable(int x, int y, int z) {
        return state(x, y, z).is(BlockTags.CLIMBABLE);
    }

    @Override
    public boolean dangerous(int x, int y, int z) {
        BlockState state = state(x, y, z);
        return state.getFluidState().is(FluidTags.LAVA) || state.is(BlockTags.FIRE) || state.is(Blocks.CACTUS)
            || state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.POWDER_SNOW)
            || state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE) || state.is(Blocks.WITHER_ROSE);
    }
}

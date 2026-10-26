package dev.blockpilot.game;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class Shapes {
    private Shapes() {
    }

    /** The centre of the block's outline shape (a lever's base, not the air above it), else of the block. */
    public static Vec3 centre(Level level, BlockPos pos) {
        VoxelShape shape = level.getBlockState(pos).getShape(level, pos);
        return shape.isEmpty() ? Vec3.atCenterOf(pos)
            : shape.bounds().getCenter().add(pos.getX(), pos.getY(), pos.getZ());
    }
}

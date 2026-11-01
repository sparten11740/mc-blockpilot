package dev.blockpilot.game;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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

    /**
     * The centre of the face the block's outline shape turns toward its neighbour in that direction (a carpet's top, a
     * fence post's side), else of the block's own face.
     */
    public static Vec3 faceToward(Level level, BlockPos pos, Direction direction) {
        VoxelShape shape = level.getBlockState(pos).getShape(level, pos);
        ShapeBounds bounds = ShapeBounds.enclosing(shape.toAabbs().stream()
            .map(box -> new ShapeBounds(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ)).toList());
        BlockFrame.Point face = bounds.faceToward(direction.getStepX(), direction.getStepY(), direction.getStepZ());
        return new Vec3(pos.getX() + face.x(), pos.getY() + face.y(), pos.getZ() + face.z());
    }
}

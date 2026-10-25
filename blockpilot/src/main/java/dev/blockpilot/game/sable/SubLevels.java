package dev.blockpilot.game.sable;

import dev.blockpilot.protocol.CommandFailure;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Sable's sub-levels as the rest of Blockpilot sees them: structures whose blocks live in a plot away from where they
 * are seen. Only {@code dev.blockpilot.compat.sable} talks to Sable; without it (or without Sable) there are none. A
 * call into Sable that fails throws {@link CommandFailure} ({@code err sable}), which code outside a command, such as
 * a frame or tick hook, must catch.
 */
public interface SubLevels {
    /** A sub-level as it lies now; angles in Minecraft's yaw, pitch and roll, velocity in blocks per second. */
    record Info(UUID id, String name, Vec3 position, Vec3 velocity, float yaw, float pitch, float roll,
        AABB worldBounds, AABB plotBounds) {
    }

    SubLevels NONE = new SubLevels() {
    };

    /** The sub-level whose plot holds the position, or null. */
    default Info at(Level level, BlockPos pos) {
        return null;
    }

    default Info info(Level level, UUID id) {
        return null;
    }

    /** The sub-levels whose world bounds meet the box. */
    default List<Info> near(Level level, AABB box) {
        return List.of();
    }

    /** The sub-level the entity stands on or rides, or null. */
    default UUID carrying(Entity entity) {
        return null;
    }

    /** Where a point in a plot is in the world as its sub-level lies now; any other point as it is. */
    default Vec3 toWorld(Level level, Vec3 point) {
        return point;
    }

    /** As {@link #toWorld}, where the client draws the sub-level in the frame being rendered. */
    default Vec3 toWorldRendered(Level level, Vec3 point, float partialTick) {
        return point;
    }

    /** The point in the sub-level's plot that is at the world point now, or null when there is no such sub-level. */
    default Vec3 toPlot(Level level, UUID id, Vec3 point) {
        return null;
    }
}

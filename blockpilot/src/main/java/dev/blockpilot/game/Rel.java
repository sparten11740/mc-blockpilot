package dev.blockpilot.game;

import dev.blockpilot.api.Fields;
import dev.blockpilot.api.Numbers;
import dev.blockpilot.api.Position;
import dev.blockpilot.game.sable.SableSupport;
import dev.blockpilot.game.sable.SubLevels;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Positions as printed to agents: relative ({@code ~dx,dy,dz}) to the player's block position, except positions in a
 * sub-level's plot, which print absolute because the plot is far from where the sub-level is seen.
 */
public final class Rel {
    private Rel() {
    }

    private static BlockPos base() {
        var player = Minecraft.getInstance().player;
        return player == null ? BlockPos.ZERO : player.blockPosition();
    }

    public static Position of(BlockPos pos) {
        if (aboard(pos) != null) {
            return absolute(pos);
        }
        BlockPos base = base();
        return Position.relative(pos.getX() - base.getX(), pos.getY() - base.getY(), pos.getZ() - base.getZ());
    }

    /** An exact point (e.g. an entity's feet), one decimal place. */
    public static Position of(Vec3 point) {
        if (aboard(BlockPos.containing(point)) != null) {
            return absolute(point);
        }
        BlockPos base = base();
        return Position.relative(round(point.x - base.getX()), round(point.y - base.getY()),
            round(point.z - base.getZ()));
    }

    /** For a block in a sub-level's plot, adds {@code sub=<name> world=<x,y,z>}: where its centre is in the world. */
    public static void sub(Fields fields, Level level, BlockPos pos) {
        SubLevels.Info subLevel = SableSupport.subLevels().at(level, pos);
        if (subLevel != null) {
            fields.put("sub", subLevel.name())
                .put("world", absolute(SableSupport.subLevels().toWorld(level, Vec3.atCenterOf(pos))));
        }
    }

    private static SubLevels.Info aboard(BlockPos pos) {
        Level level = Minecraft.getInstance().level;
        return level == null ? null : SableSupport.subLevels().at(level, pos);
    }

    public static Position absolute(Vec3 point) {
        return Position.absolute(Numbers.round(point.x), Numbers.round(point.y), Numbers.round(point.z));
    }

    public static Position absolute(BlockPos pos) {
        return Position.absolute(pos.getX(), pos.getY(), pos.getZ());
    }

    private static double round(double value) {
        return Math.round(value * 10) / 10.0;
    }

    public static BlockPos block(Position absolute) {
        return BlockPos.containing(absolute.x(), absolute.y(), absolute.z());
    }
}

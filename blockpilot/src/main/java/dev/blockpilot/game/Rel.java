package dev.blockpilot.game;

import dev.blockpilot.api.Numbers;
import dev.blockpilot.api.Position;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/** Positions as printed to agents: relative ({@code ~dx,dy,dz}) to the player's block position. */
public final class Rel {
    private Rel() {
    }

    private static BlockPos base() {
        var player = Minecraft.getInstance().player;
        return player == null ? BlockPos.ZERO : player.blockPosition();
    }

    public static Position of(BlockPos pos) {
        BlockPos base = base();
        return Position.relative(pos.getX() - base.getX(), pos.getY() - base.getY(), pos.getZ() - base.getZ());
    }

    /** An exact point (e.g. an entity's feet), one decimal place. */
    public static Position of(Vec3 point) {
        BlockPos base = base();
        return Position.relative(round(point.x - base.getX()), round(point.y - base.getY()),
            round(point.z - base.getZ()));
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

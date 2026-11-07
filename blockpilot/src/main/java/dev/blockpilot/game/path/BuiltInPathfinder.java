package dev.blockpilot.game.path;

import dev.blockpilot.api.Action;
import dev.blockpilot.api.Position;
import java.util.function.Supplier;
import net.minecraft.world.phys.Vec3;

/** Blockpilot's own pathfinder: walks, steps up, drops, swims and climbs, and never breaks or places blocks. */
final class BuiltInPathfinder implements Pathfinder {
    static final String NAME = "blockpilot";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public Action walk(int session, Supplier<Position> target, double near) {
        return new Navigator(session, () -> {
            Position at = target.get();
            return at == null ? null : new Vec3(at.x(), at.y(), at.z());
        }, near);
    }
}

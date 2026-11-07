package dev.blockpilot.game.path;

import dev.blockpilot.api.Position;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.world.phys.Vec3;

/**
 * The pathfinders in order of preference, the built-in one last as it is always available; the first available one
 * walks. Another pathfinding mod plugs in as one more adapter in {@link Installed}.
 */
public final class Pathfinders {
    /** Built on first use, in the game, as the adapters look for their mods then. */
    private static final class Installed {
        static final Pathfinders VALUE = new Pathfinders(List.of(new BuiltInPathfinder()));
    }

    private final List<Pathfinder> preferred;

    Pathfinders(List<Pathfinder> preferred) {
        this.preferred = List.copyOf(preferred);
    }

    public static Pathfinders installed() {
        return Installed.VALUE;
    }

    /** The first available pathfinder, asked at each call, as one may only become available in a world. */
    public Pathfinder current() {
        for (Pathfinder pathfinder : preferred) {
            if (pathfinder.available()) {
                return pathfinder;
            }
        }
        throw new IllegalStateException("no pathfinder is available, not even the built-in one");
    }

    /** A target the game reads as a point, for the port, which takes positions. */
    public static Supplier<Position> positions(Supplier<Vec3> target) {
        return () -> {
            Vec3 at = target.get();
            return at == null ? null : Position.absolute(at.x, at.y, at.z);
        };
    }

    /** Whether the pathfinder is Blockpilot's own, whose replies don't name it. */
    public static boolean builtIn(Pathfinder pathfinder) {
        return BuiltInPathfinder.NAME.equals(pathfinder.name());
    }
}

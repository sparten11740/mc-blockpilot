package dev.blockpilot.game.path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.blockpilot.api.Action;
import dev.blockpilot.api.Position;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class PathfindersTest {
    /** An adapter that is available or not as the test says, and never walks. */
    private record Fake(String name, boolean available) implements Pathfinder {
        @Override
        public Action walk(int session, Supplier<Position> target, double near) {
            throw new UnsupportedOperationException();
        }
    }

    @Test
    void theFirstAvailablePathfinderWalks() {
        Fake first = new Fake("first", true);
        Fake second = new Fake("second", true);
        assertSame(first, new Pathfinders(List.of(first, second, new BuiltInPathfinder())).current());
    }

    @Test
    void anUnavailablePathfinderIsSkipped() {
        Fake gone = new Fake("gone", false);
        Fake next = new Fake("next", true);
        assertSame(next, new Pathfinders(List.of(gone, next, new BuiltInPathfinder())).current());
    }

    @Test
    void theBuiltInPathfinderIsTheLastResort() {
        BuiltInPathfinder builtIn = new BuiltInPathfinder();
        Pathfinder chosen = new Pathfinders(List.of(new Fake("gone", false), builtIn)).current();
        assertSame(builtIn, chosen);
        assertTrue(Pathfinders.builtIn(chosen));
        assertFalse(Pathfinders.builtIn(new Fake("other", true)));
    }

    @Test
    void withNoneAvailableItIsABug() {
        assertThrows(IllegalStateException.class, () -> new Pathfinders(List.of(new Fake("gone", false))).current());
    }
}

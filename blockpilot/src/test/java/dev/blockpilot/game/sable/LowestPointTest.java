package dev.blockpilot.game.sable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.joml.Vector3d;
import org.joml.Vector3i;
import org.junit.jupiter.api.Test;

class LowestPointTest {
    private static final Vector3d LEVEL = new Vector3d(0, 1, 0);
    /** Rolled 30° so that plot +x goes down. */
    private static final Vector3d ROLLED = new Vector3d(-Math.sin(Math.toRadians(30)), Math.cos(Math.toRadians(30)), 0);

    private static LowestPoint.Part box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
        LowestPoint.Solid solid) {
        return new LowestPoint.Part(new Vector3i(minX, minY, minZ), new Vector3i(maxX, maxY, maxZ), solid);
    }

    @Test
    void findsTheBottomLayerOfALevelSubLevel() {
        LowestPoint.Part hull = box(0, 0, 0, 15, 15, 15, (x, y, z) -> y >= 4);
        Vector3i lowest = LowestPoint.lowestBlock(List.of(hull), LEVEL);
        assertEquals(4, lowest.y());
        assertEquals(new Vector3d(lowest.x(), 4, lowest.z()), LowestPoint.corner(lowest, LEVEL));
    }

    @Test
    void findsTheLowWingtipOfARolledSubLevel() {
        // A wing along x at y 8 and a keel under its middle at y 6.
        LowestPoint.Part wing = box(0, 0, 0, 15, 15, 15, (x, y, z) -> y == 8 || x == 7 && y == 6);
        Vector3i lowest = LowestPoint.lowestBlock(List.of(wing), ROLLED);
        assertEquals(new Vector3i(15, 8, 0), lowest);
        assertEquals(new Vector3d(16, 8, 0), LowestPoint.corner(lowest, ROLLED));
    }

    @Test
    void leavesPartsThatCannotReachLowerUnread() {
        AtomicInteger reads = new AtomicInteger();
        LowestPoint.Part high = box(0, 16, 0, 15, 31, 15, (x, y, z) -> reads.incrementAndGet() > 0);
        LowestPoint.Part low = box(0, 0, 0, 15, 15, 15, (x, y, z) -> y == 15);
        assertEquals(15, LowestPoint.lowestBlock(List.of(high, low), LEVEL).y());
        assertEquals(0, reads.get());
    }

    @Test
    void reachesToThePartsCornerFurthestDown() {
        LowestPoint.Part section = box(0, 0, 0, 15, 15, 15, (x, y, z) -> true);
        assertEquals(-7.5, LowestPoint.reach(section, ROLLED), 1.0E-9);
        assertEquals(0, LowestPoint.reach(section, LEVEL));
    }

    @Test
    void findsNothingInAir() {
        assertNull(LowestPoint.lowestBlock(List.of(box(0, 0, 0, 15, 15, 15, (x, y, z) -> false)), LEVEL));
    }
}

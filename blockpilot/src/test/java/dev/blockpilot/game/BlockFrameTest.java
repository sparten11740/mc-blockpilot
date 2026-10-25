package dev.blockpilot.game;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class BlockFrameTest {
    private static final double EPSILON = 1e-9;
    /** The upper left of a model's front face, seen from in front of a block facing north. */
    private static final BlockFrame.Point FRONT_UPPER_LEFT = new BlockFrame.Point(0.75, 0.75, 0);

    private static void assertPoint(double x, double y, double z, BlockFrame.Point point) {
        assertEquals(x, point.x(), EPSILON, "x");
        assertEquals(y, point.y(), EPSILON, "y");
        assertEquals(z, point.z(), EPSILON, "z");
    }

    @Test
    void northIsTheModelsOwnFrame() {
        assertPoint(0.75, 0.75, 0, BlockFrame.ofFacing("north").toBlock(FRONT_UPPER_LEFT));
    }

    @Test
    void theFrontTurnsWithTheFacing() {
        // The front face lies on the block's side that the facing names; up stays up.
        assertPoint(1, 0.75, 0.75, BlockFrame.ofFacing("east").toBlock(FRONT_UPPER_LEFT));
        assertPoint(0.25, 0.75, 1, BlockFrame.ofFacing("south").toBlock(FRONT_UPPER_LEFT));
        assertPoint(0, 0.75, 0.25, BlockFrame.ofFacing("west").toBlock(FRONT_UPPER_LEFT));
    }

    @Test
    void otherFacingsAreUnturned() {
        assertEquals(BlockFrame.UNTURNED, BlockFrame.ofFacing("up"));
        assertEquals(BlockFrame.UNTURNED, BlockFrame.ofFacing("down"));
        assertPoint(0.75, 0.75, 0, BlockFrame.UNTURNED.toBlock(FRONT_UPPER_LEFT));
    }

    @Test
    void fromBlockUndoesToBlock() {
        BlockFrame.Point point = new BlockFrame.Point(0.13, 0.42, 0.87);
        for (String facing : new String[] {"north", "east", "south", "west"}) {
            BlockFrame frame = BlockFrame.ofFacing(facing);
            BlockFrame.Point back = frame.fromBlock(frame.toBlock(point));
            assertPoint(point.x(), point.y(), point.z(), back);
        }
    }

    @Test
    void theCentreStaysPut() {
        assertPoint(0.5, 0.3, 0.5, BlockFrame.ofFacing("west").toBlock(new BlockFrame.Point(0.5, 0.3, 0.5)));
    }
}

package dev.blockpilot.game;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Where place aims on the block it places against: the face of that block's shape, not of its cell. */
class ShapeBoundsTest {
    private static final double EPSILON = 1e-9;

    private static void assertPoint(double x, double y, double z, BlockFrame.Point point) {
        assertEquals(x, point.x(), EPSILON, "x");
        assertEquals(y, point.y(), EPSILON, "y");
        assertEquals(z, point.z(), EPSILON, "z");
    }

    @Test
    void aCarpetIsAimedAtOnItsTopNotAtTheTopOfItsCell() {
        // Regression: aiming at the cell's top put the point 15/16 of a block above a carpet, in the air, so the
        // crosshair passed over it and place failed with "cannot see the face".
        ShapeBounds carpet = new ShapeBounds(0, 0, 0, 1, 1.0 / 16, 1);
        assertPoint(0.5, 1.0 / 16, 0.5, carpet.faceToward(0, 1, 0));
    }

    @Test
    void aFencePostIsAimedAtOnItsSide() {
        ShapeBounds post = new ShapeBounds(0.375, 0, 0.375, 0.625, 1, 0.625);
        assertPoint(0.5, 0.5, 0.625, post.faceToward(0, 0, 1));
        assertPoint(0.375, 0.5, 0.5, post.faceToward(-1, 0, 0));
    }

    @Test
    void aShapeWithoutBoxesIsAimedAtAsTheWholeCell() {
        assertEquals(ShapeBounds.FULL, ShapeBounds.enclosing(List.of()));
    }

    @Test
    void theBoundsEncloseAllOfAShapesBoxes() {
        // Bottom stairs: a slab and a step on its north half.
        ShapeBounds stairs = ShapeBounds.enclosing(List.of(new ShapeBounds(0, 0, 0, 1, 0.5, 1),
            new ShapeBounds(0, 0.5, 0, 1, 1, 0.5)));
        assertEquals(new ShapeBounds(0, 0, 0, 1, 1, 1), stairs);
        ShapeBounds post = new ShapeBounds(0.375, 0, 0.375, 0.625, 1, 0.625);
        assertEquals(post, ShapeBounds.enclosing(List.of(post)));
    }

    @Test
    void aFullBlockIsAimedAtTheMiddleOfItsCellsFace() {
        assertPoint(0, 0.5, 0.5, ShapeBounds.FULL.faceToward(-1, 0, 0));
        assertPoint(0.5, 0, 0.5, ShapeBounds.FULL.faceToward(0, -1, 0));
        assertPoint(0.5, 0.5, 1, ShapeBounds.FULL.faceToward(0, 0, 1));
    }
}

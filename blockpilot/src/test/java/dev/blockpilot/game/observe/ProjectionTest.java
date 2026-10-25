package dev.blockpilot.game.observe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class ProjectionTest {
    private static final float[] IDENTITY = {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};
    /** A 90° field of view, square, near 0.05 and far 100, column-major as the renderer keeps it. */
    private static final float[] PERSPECTIVE = {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, -100.05f / 99.95f, -1, 0, 0,
        -2 * 100 * 0.05f / 99.95f, 0};

    private static void assertRect(FrameRect expected, FrameRect actual) {
        assertEquals(expected.x0(), actual.x0(), 1e-6);
        assertEquals(expected.y0(), actual.y0(), 1e-6);
        assertEquals(expected.x1(), actual.x1(), 1e-6);
        assertEquals(expected.y1(), actual.y1(), 1e-6);
    }

    @Test
    void aPointAheadIsFramedAroundItsProjection() {
        Projection camera = new Projection(0, 0, 0, IDENTITY, PERSPECTIVE);
        assertRect(new FrameRect(0.45, 0.45, 0.55, 0.55), camera.around(0, 0, -10, 1));
        // Up in the world is up in the frame, which counts from the top.
        assertRect(new FrameRect(0.45, 0.2, 0.55, 0.3), camera.around(0, 5, -10, 1));
        assertRect(new FrameRect(0.7, 0.45, 0.8, 0.55), new Projection(-5, 0, 0, IDENTITY, PERSPECTIVE)
            .around(0, 0, -10, 1));
    }

    @Test
    void theRegionIsCutToTheFrame() {
        assertRect(new FrameRect(0.925, 0.45, 1, 0.55), new Projection(0, 0, 0, IDENTITY, PERSPECTIVE)
            .around(9.5, 0, -10, 1));
        // Half a block ahead, a block to each side covers more than the frame.
        assertRect(FrameRect.ALL, new Projection(0, 0, 0, IDENTITY, PERSPECTIVE).around(0, 0, -0.5, 1));
    }

    @Test
    void pointsBehindOrBesideTheFrameAreOffscreen() {
        Projection camera = new Projection(0, 0, 0, IDENTITY, PERSPECTIVE);
        assertNull(camera.around(0, 0, 10, 1));
        assertNull(camera.around(20, 0, -10, 1));
        assertNull(camera.around(0, -20, -10, 1));
    }

    @Test
    void theViewRotatesTheWorld() {
        // Turned to look along +x: world x becomes view -z.
        float[] towardsX = {0, 0, -1, 0, 0, 1, 0, 0, 1, 0, 0, 0, 0, 0, 0, 1};
        assertRect(new FrameRect(0.45, 0.45, 0.55, 0.55), new Projection(0, 0, 0, towardsX, PERSPECTIVE)
            .around(10, 0, 0, 1));
        assertNull(new Projection(0, 0, 0, towardsX, PERSPECTIVE).around(0, 0, -10, 1));
    }

    @Test
    void theMatricesAreCopies() {
        float[] view = IDENTITY.clone();
        Projection camera = new Projection(0, 0, 0, view, PERSPECTIVE);
        view[12] = 100;
        assertRect(new FrameRect(0.45, 0.45, 0.55, 0.55), camera.around(0, 0, -10, 1));
    }
}

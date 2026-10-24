package dev.blockpilot.game.perf;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class StatsTest {
    @Test
    void averagesPercentilesAndMax() {
        double[] values = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
        assertEquals(5.5, Stats.average(values));
        assertEquals(10, Stats.percentile(values, 0.95));
        assertEquals(5, Stats.percentile(values, 0.5));
        assertEquals(10, Stats.max(values));
        assertEquals(0, Stats.average(new double[0]));
    }

    @Test
    void lowFpsUsesTheSlowestFrames() {
        double[] frames = new double[100];
        java.util.Arrays.fill(frames, 10);
        frames[99] = 50;
        assertEquals(20, Stats.lowFps(frames, 0.01), 0.001);
        assertEquals(0, Stats.lowFps(new double[0], 0.01));
    }
}

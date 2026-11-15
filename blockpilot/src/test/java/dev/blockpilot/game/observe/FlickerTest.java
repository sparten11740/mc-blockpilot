package dev.blockpilot.game.observe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.Test;

class FlickerTest {
    /** Frames drawn about 55 times a second, unevenly, over the ticks given. */
    private static double[] frameTimes(double ticks, long seed) {
        Random random = new Random(seed);
        int count = (int) (ticks / 20 * 55);
        double[] times = new double[count];
        double at = 0;
        for (int index = 0; index < count; index++) {
            times[index] = at;
            at += (20.0 / 55) * (0.6 + 0.8 * random.nextDouble());
        }
        return times;
    }

    @Test
    void aLampThatBlinksEveryOtherTickFlickersAtTenHertz() {
        double[] times = frameTimes(40, 1);
        double[] values = new double[times.length];
        for (int index = 0; index < times.length; index++) {
            values[index] = ((int) Math.floor(times[index])) % 2 == 0 ? 240 : 20;
        }
        Flicker.Rhythm rhythm = Flicker.strongest(times, values);
        assertNotNull(rhythm);
        assertEquals(10, rhythm.hertz(), 0.3);
        assertEquals(2, rhythm.periodTicks(), 0.1);
        assertTrue(rhythm.amplitude() > 100, "a square wave's fundamental is larger than half its swing");
    }

    @Test
    void aSlowPulseIsFoundToo() {
        double[] times = frameTimes(80, 2);
        double[] values = new double[times.length];
        for (int index = 0; index < times.length; index++) {
            values[index] = 128 + 40 * Math.sin(2 * Math.PI * 1.5 * times[index] / 20);
        }
        Flicker.Rhythm rhythm = Flicker.strongest(times, values);
        assertNotNull(rhythm);
        assertEquals(1.5, rhythm.hertz(), 0.1);
        assertEquals(40, rhythm.amplitude(), 3);
    }

    @Test
    void aSteadyRegionHasNoRhythm() {
        double[] times = frameTimes(40, 3);
        double[] values = new double[times.length];
        Random noise = new Random(4);
        for (int index = 0; index < times.length; index++) {
            values[index] = 90 + noise.nextDouble() * 0.5;
        }
        assertNull(Flicker.strongest(times, values));
    }

    @Test
    void tooFewFramesTellNothing() {
        assertNull(Flicker.strongest(new double[] {0, 1, 2}, new double[] {0, 255, 0}));
    }
}

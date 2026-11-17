package dev.blockpilot.game.observe;

import dev.blockpilot.api.Duration;

/**
 * The dominant rhythm in a brightness series sampled at uneven times, as frames are drawn: a periodogram over the
 * frequencies that repeat at least twice in the series, up to half their average rate, kept only when the series
 * swings to and fro at least twice, so a light switched on, or on and off, once is no rhythm. Times are in client
 * ticks, so the frequency is in cycles per game second (20 ticks) and holds under a slowed tick rate.
 */
final class Flicker {
    /** Below this amplitude, in luma (0 to 255), a region counts as steady. */
    static final double STEADY = 1.0;
    /** Candidate frequencies per cycle the series can hold, for a finer peak than its length alone gives. */
    private static final int STEPS_PER_CYCLE = 8;
    /** Swings from one side of the mean to the other that a rhythm repeating twice makes: high, low, high, low. */
    private static final int REPEATED_SWINGS = 3;

    /** The strongest rhythm: its frequency in hertz and its amplitude in luma, or null for a steady series. */
    record Rhythm(double hertz, double amplitude) {
        double periodTicks() {
            return Duration.TICKS_PER_SECOND / hertz;
        }
    }

    private Flicker() {
    }

    /** The strongest rhythm in the values at the times (client ticks, rising), or null when none stands out. */
    static Rhythm strongest(double[] ticks, double[] values) {
        int count = values.length;
        if (count < 4 || ticks[count - 1] <= ticks[0]) {
            return null;
        }
        double span = ticks[count - 1] - ticks[0];
        double mean = 0;
        for (double value : values) {
            mean += value;
        }
        mean /= count;
        double lowest = 2 / span;
        double highest = (count - 1) / (2 * span);
        double step = lowest / STEPS_PER_CYCLE;
        double bestFrequency = 0;
        double bestPower = 0;
        for (double frequency = lowest; frequency <= highest; frequency += step) {
            double cosines = 0;
            double sines = 0;
            for (int index = 0; index < count; index++) {
                double angle = 2 * Math.PI * frequency * (ticks[index] - ticks[0]);
                cosines += (values[index] - mean) * Math.cos(angle);
                sines += (values[index] - mean) * Math.sin(angle);
            }
            double power = cosines * cosines + sines * sines;
            if (power > bestPower) {
                bestPower = power;
                bestFrequency = frequency;
            }
        }
        double amplitude = 2 * Math.sqrt(bestPower) / count;
        // A quarter of the amplitude still catches short flashes, whose fundamental is small beside their swing.
        return amplitude < STEADY || swings(values, mean, amplitude / 4) < REPEATED_SWINGS ? null
            : new Rhythm(bestFrequency * Duration.TICKS_PER_SECOND, amplitude);
    }

    /** How often the values go from beyond {@code band} on one side of the mean to beyond it on the other. */
    private static int swings(double[] values, double mean, double band) {
        int swings = 0;
        int side = 0;
        for (double value : values) {
            int now = value > mean + band ? 1 : value < mean - band ? -1 : 0;
            if (now != 0 && now != side) {
                swings += side == 0 ? 0 : 1;
                side = now;
            }
        }
        return swings;
    }
}

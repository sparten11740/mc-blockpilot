package dev.blockpilot.game.perf;

import java.util.Arrays;

/** Summary statistics over a series of samples (milliseconds, frame times). */
public final class Stats {
    private Stats() {
    }

    public static double average(double[] values) {
        return values.length == 0 ? 0 : Arrays.stream(values).sum() / values.length;
    }

    public static double max(double[] values) {
        return Arrays.stream(values).max().orElse(0);
    }

    /** The value below which the given fraction of samples lie (nearest-rank), e.g. 0.95 for p95. */
    public static double percentile(double[] values, double fraction) {
        if (values.length == 0) {
            return 0;
        }
        double[] sorted = values.clone();
        Arrays.sort(sorted);
        int rank = (int) Math.ceil(fraction * sorted.length) - 1;
        return sorted[Math.max(0, Math.min(sorted.length - 1, rank))];
    }

    /** "1% low" FPS: the average frame rate over the slowest 1% of frames (at least one frame). */
    public static double lowFps(double[] frameMillis, double fraction) {
        if (frameMillis.length == 0) {
            return 0;
        }
        double[] sorted = frameMillis.clone();
        Arrays.sort(sorted);
        int count = Math.max(1, (int) Math.round(sorted.length * fraction));
        double slowest = 0;
        for (int index = sorted.length - count; index < sorted.length; index++) {
            slowest += sorted[index];
        }
        double averageMillis = slowest / count;
        return averageMillis <= 0 ? 0 : 1000 / averageMillis;
    }
}

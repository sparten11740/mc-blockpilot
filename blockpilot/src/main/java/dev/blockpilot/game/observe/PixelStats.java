package dev.blockpilot.game.observe;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The mean colour of a part of a frame, and the mean, maximum and 90th percentile of its luma (Rec. 601 weights),
 * all 0–255. Free of game classes.
 */
public record PixelStats(double r, double g, double b, double luma, int max, int p90) {
    /** A frame's pixels, packed as NativeImage keeps them: alpha, blue, green, red from the high byte down. */
    public interface Frame {
        int width();

        int height();

        int abgr(int x, int y);
    }

    public static PixelStats of(Frame frame, FrameRect part) {
        int[] area = part.pixels(frame.width(), frame.height());
        long red = 0;
        long green = 0;
        long blue = 0;
        double luma = 0;
        int[] histogram = new int[256];
        for (int y = area[1]; y < area[1] + area[3]; y++) {
            for (int x = area[0]; x < area[0] + area[2]; x++) {
                int color = frame.abgr(x, y);
                int r = color & 0xFF;
                int g = color >> 8 & 0xFF;
                int b = color >> 16 & 0xFF;
                double value = 0.299 * r + 0.587 * g + 0.114 * b;
                red += r;
                green += g;
                blue += b;
                luma += value;
                histogram[(int) Math.round(value)]++;
            }
        }
        long count = (long) area[2] * area[3];
        int max = 255;
        while (histogram[max] == 0) {
            max--;
        }
        // The smallest value at or below which 90% of the pixels lie.
        long rank = (long) Math.ceil(count * 0.9);
        int p90 = -1;
        long seen = 0;
        while (seen < rank) {
            seen += histogram[++p90];
        }
        return new PixelStats(red / (double) count, green / (double) count, blue / (double) count, luma / count, max,
            p90);
    }

    /** The statistics as fields named {@code r g b luma max p90}, means rounded to one decimal. */
    public Map<String, Object> asMap() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("r", round(r));
        fields.put("g", round(g));
        fields.put("b", round(b));
        fields.put("luma", round(luma));
        fields.put("max", max);
        fields.put("p90", p90);
        return fields;
    }

    private static double round(double value) {
        return Math.round(value * 10) / 10.0;
    }
}

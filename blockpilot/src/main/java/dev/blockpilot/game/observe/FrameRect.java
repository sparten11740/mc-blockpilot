package dev.blockpilot.game.observe;

import dev.blockpilot.api.CommandException;

/** A part of the frame in fractions of its width and height, measured from the top left. */
public record FrameRect(double x0, double y0, double x1, double y1) {
    public static final FrameRect ALL = new FrameRect(0, 0, 1, 1);
    /** The middle tenth of each axis. */
    public static final FrameRect CENTRE = new FrameRect(0.45, 0.45, 0.55, 0.55);
    private static final double EDGE = 1e-6;

    /** {@code x0,y0,x1,y1}, each between 0 and 1, with x0 < x1 and y0 < y1. */
    public static FrameRect parse(String text) throws CommandException {
        String[] parts = text.split(",");
        CommandException invalid = new CommandException("syntax",
            "a frame part is x0,y0,x1,y1 in fractions of the frame (0..1, from the top left), not " + text);
        if (parts.length != 4) {
            throw invalid;
        }
        double[] values = new double[4];
        for (int index = 0; index < 4; index++) {
            try {
                values[index] = Double.parseDouble(parts[index]);
            } catch (NumberFormatException e) {
                throw invalid;
            }
            if (!(values[index] >= 0 && values[index] <= 1)) {
                throw invalid;
            }
        }
        if (values[0] >= values[2] || values[1] >= values[3]) {
            throw invalid;
        }
        return new FrameRect(values[0], values[1], values[2], values[3]);
    }

    /**
     * The pixel rectangle {x, y, width, height} in a frame of that size, at least one pixel each way; edges within a
     * millionth of a pixel count as on it, so 0.55 of 100 is 55.
     */
    public int[] pixels(int width, int height) {
        int left = Math.min(width - 1, (int) Math.floor(x0 * width + EDGE));
        int top = Math.min(height - 1, (int) Math.floor(y0 * height + EDGE));
        int right = Math.max(left + 1, (int) Math.ceil(x1 * width - EDGE));
        int bottom = Math.max(top + 1, (int) Math.ceil(y1 * height - EDGE));
        return new int[] {left, top, Math.min(width, right) - left, Math.min(height, bottom) - top};
    }

    public boolean isWhole() {
        return equals(ALL);
    }
}

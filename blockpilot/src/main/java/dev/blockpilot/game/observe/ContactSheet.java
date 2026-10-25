package dev.blockpilot.game.observe;

import java.util.Map;

/**
 * The grid a burst's frames are laid out in, and the frame labels, drawn with a built-in 3×5 pixel font (the game
 * can't draw text into images, and AWT is unreliable inside its process). Free of game classes; pixels go through a
 * {@link Canvas}.
 */
public final class ContactSheet {
    /** The widest sheet: keeps one image within what vision models read without downscaling much. */
    public static final int MAX_WIDTH = 1536;
    /** The widest tile when no width is asked for. */
    public static final int DEFAULT_TILE_WIDTH = 512;
    /** Font pixels per glyph pixel. */
    private static final int SCALE = 2;
    private static final int GLYPH_WIDTH = 3;
    private static final int GLYPH_HEIGHT = 5;
    private static final int PADDING = 2;
    public static final int TEXT = 0xFFFFFFFF;
    public static final int BACKDROP = 0xC0000000;
    public static final int BACKGROUND = 0xFF202020;

    /** Rows of 3 bits, top to bottom, the high bit on the left. */
    private static final Map<Character, int[]> GLYPHS = Map.ofEntries(
        Map.entry('0', new int[] {7, 5, 5, 5, 7}), Map.entry('1', new int[] {2, 6, 2, 2, 7}),
        Map.entry('2', new int[] {7, 1, 7, 4, 7}), Map.entry('3', new int[] {7, 1, 7, 1, 7}),
        Map.entry('4', new int[] {5, 5, 7, 1, 1}), Map.entry('5', new int[] {7, 4, 7, 1, 7}),
        Map.entry('6', new int[] {7, 4, 7, 5, 7}), Map.entry('7', new int[] {7, 1, 1, 1, 1}),
        Map.entry('8', new int[] {7, 5, 7, 5, 7}), Map.entry('9', new int[] {7, 5, 7, 1, 7}),
        Map.entry('t', new int[] {2, 7, 2, 2, 3}), Map.entry('#', new int[] {5, 7, 5, 7, 5}),
        Map.entry('-', new int[] {0, 0, 7, 0, 0}), Map.entry(' ', new int[] {0, 0, 0, 0, 0}));

    /** Where pixels go; colours are opaque white, the translucent black backdrop or the sheet background. */
    public interface Canvas {
        void set(int x, int y, int color);
    }

    /** Columns and rows of the grid and the size of each tile. */
    public record Layout(int columns, int rows, int tileWidth, int tileHeight) {
        public int width() {
            return columns * tileWidth;
        }

        public int height() {
            return rows * tileHeight;
        }

        public int x(int frame) {
            return frame % columns * tileWidth;
        }

        public int y(int frame) {
            return frame / columns * tileHeight;
        }
    }

    private ContactSheet() {
    }

    private static int columns(int frames) {
        return (int) Math.ceil(Math.sqrt(frames));
    }

    /**
     * The width to scale frames to: as asked (or {@link #DEFAULT_TILE_WIDTH}), but never wider than
     * {@link #MAX_WIDTH} split over the {@code ceil(sqrt(frames))} columns.
     */
    public static int tileWidth(int frames, Integer requested) {
        int wanted = requested == null ? DEFAULT_TILE_WIDTH : requested;
        return Math.max(16, Math.min(wanted, MAX_WIDTH / columns(frames)));
    }

    /** The grid for that many tiles of that size. */
    public static Layout layout(int frames, int tileWidth, int tileHeight) {
        int columns = columns(frames);
        return new Layout(columns, (frames + columns - 1) / columns, tileWidth, tileHeight);
    }

    /** The label of a frame: its number and tick. */
    public static String label(int frame, long tick) {
        return "#" + frame + " t" + tick;
    }

    /** Draws the text on a backdrop with its top left corner at the point; characters without a glyph are blank. */
    public static void draw(Canvas canvas, int left, int top, String text) {
        int advance = (GLYPH_WIDTH + 1) * SCALE;
        int width = text.length() * advance - SCALE + 2 * PADDING;
        int height = GLYPH_HEIGHT * SCALE + 2 * PADDING;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                canvas.set(left + x, top + y, BACKDROP);
            }
        }
        for (int index = 0; index < text.length(); index++) {
            int[] rows = GLYPHS.getOrDefault(text.charAt(index), GLYPHS.get(' '));
            int glyphLeft = left + PADDING + index * advance;
            for (int row = 0; row < GLYPH_HEIGHT; row++) {
                for (int column = 0; column < GLYPH_WIDTH; column++) {
                    if ((rows[row] >> (GLYPH_WIDTH - 1 - column) & 1) == 1) {
                        fill(canvas, glyphLeft + column * SCALE, top + PADDING + row * SCALE);
                    }
                }
            }
        }
    }

    private static void fill(Canvas canvas, int left, int top) {
        for (int y = 0; y < SCALE; y++) {
            for (int x = 0; x < SCALE; x++) {
                canvas.set(left + x, top + y, TEXT);
            }
        }
    }
}

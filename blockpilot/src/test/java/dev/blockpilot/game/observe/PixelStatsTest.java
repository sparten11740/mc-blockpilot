package dev.blockpilot.game.observe;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import java.util.function.IntBinaryOperator;
import org.junit.jupiter.api.Test;

class PixelStatsTest {
    private static PixelStats.Frame frame(int width, int height, IntBinaryOperator abgr) {
        return new PixelStats.Frame() {
            @Override
            public int width() {
                return width;
            }

            @Override
            public int height() {
                return height;
            }

            @Override
            public int abgr(int x, int y) {
                return abgr.applyAsInt(x, y);
            }
        };
    }

    private static int gray(int value) {
        return 0xFF000000 | value << 16 | value << 8 | value;
    }

    @Test
    void halfBlackHalfWhite() {
        PixelStats.Frame frame = frame(10, 10, (x, y) -> gray(x < 5 ? 0 : 255));
        PixelStats all = PixelStats.of(frame, FrameRect.ALL);
        assertEquals(127.5, all.luma(), 1e-6);
        assertEquals(127.5, all.r(), 1e-6);
        assertEquals(255, all.max());
        assertEquals(255, all.p90());
        PixelStats left = PixelStats.of(frame, new FrameRect(0, 0, 0.5, 1));
        assertEquals(0, left.max());
        assertEquals(0, left.p90());
        assertEquals(0, left.luma(), 1e-6);
    }

    @Test
    void channelsAndLumaUseRec601() {
        // Pure red, packed as alpha, blue, green, red.
        PixelStats red = PixelStats.of(frame(2, 2, (x, y) -> 0xFF0000FF), FrameRect.ALL);
        assertEquals(255, red.r(), 1e-6);
        assertEquals(0, red.g(), 1e-6);
        assertEquals(0, red.b(), 1e-6);
        assertEquals(0.299 * 255, red.luma(), 1e-6);
        assertEquals(76, red.max());
        PixelStats blue = PixelStats.of(frame(2, 2, (x, y) -> 0xFFFF0000), FrameRect.ALL);
        assertEquals(255, blue.b(), 1e-6);
        assertEquals(29, blue.p90());
    }

    @Test
    void theNinetiethPercentileIsTheValueBelowWhichNineInTenLie() {
        PixelStats ramp = PixelStats.of(frame(10, 1, (x, y) -> gray(x * 10)), FrameRect.ALL);
        assertEquals(80, ramp.p90());
        assertEquals(90, ramp.max());
        assertEquals(45, ramp.luma(), 1e-6);
        PixelStats one = PixelStats.of(frame(1, 1, (x, y) -> gray(7)), FrameRect.ALL);
        assertEquals(7, one.p90());
    }

    @Test
    void fieldsAreRounded() {
        assertEquals(Map.of("r", 1.3, "g", 2.0, "b", 3.0, "luma", 4.6, "max", 5, "p90", 6),
            new PixelStats(1.25, 2, 3.04, 4.55, 5, 6).asMap());
    }
}

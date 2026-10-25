package dev.blockpilot.game.observe;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.blockpilot.api.CommandException;
import java.util.List;
import org.junit.jupiter.api.Test;

class FrameRectTest {
    @Test
    void partsAreFractionsFromTheTopLeft() throws CommandException {
        assertEquals(new FrameRect(0.3, 0.25, 0.7, 0.75), FrameRect.parse("0.3,0.25,0.7,0.75"));
        assertTrue(FrameRect.parse("0,0,1,1").isWhole());
        assertFalse(FrameRect.CENTRE.isWhole());
        for (String bad : List.of("0.5,0.5,0.4,0.6", "0,0.5,1,0.5", "1,2,3", "a,b,c,d", "-0.1,0,1,1", "0,0,1.5,1",
            "NaN,0,1,1")) {
            CommandException error = assertThrows(CommandException.class, () -> FrameRect.parse(bad), bad);
            assertEquals("syntax", error.code());
        }
    }

    @Test
    void partsCoverWholePixelsAndAtLeastOne() {
        assertArrayEquals(new int[] {30, 15, 40, 20}, new FrameRect(0.3, 0.3, 0.7, 0.7).pixels(100, 50));
        assertArrayEquals(new int[] {0, 0, 100, 50}, FrameRect.ALL.pixels(100, 50));
        assertArrayEquals(new int[] {5, 5, 1, 1}, new FrameRect(0.5, 0.5, 0.501, 0.501).pixels(10, 10));
        assertArrayEquals(new int[] {9, 9, 1, 1}, new FrameRect(0.999, 0.999, 1, 1).pixels(10, 10));
        assertArrayEquals(new int[] {45, 45, 10, 10}, FrameRect.CENTRE.pixels(100, 100));
    }
}

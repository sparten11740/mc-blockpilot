package dev.blockpilot.game.observe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ContactSheetTest {
    @Test
    void tilesShareTheWidestSheet() {
        assertEquals(512, ContactSheet.tileWidth(8, null));
        assertEquals(512, ContactSheet.tileWidth(8, 1000));
        assertEquals(300, ContactSheet.tileWidth(8, 300));
        assertEquals(700, ContactSheet.tileWidth(2, 700));
        assertEquals(192, ContactSheet.tileWidth(60, null));
        assertEquals(16, ContactSheet.tileWidth(4, 10));
    }

    @Test
    void framesFillAGridOfCeilSqrtColumns() {
        ContactSheet.Layout eight = ContactSheet.layout(8, 512, 288);
        assertEquals(3, eight.columns());
        assertEquals(3, eight.rows());
        assertEquals(1536, eight.width());
        assertEquals(864, eight.height());
        assertEquals(512, eight.x(4));
        assertEquals(288, eight.y(4));
        assertEquals(0, eight.x(6));
        assertEquals(576, eight.y(6));
        ContactSheet.Layout two = ContactSheet.layout(2, 100, 50);
        assertEquals(2, two.columns());
        assertEquals(1, two.rows());
        assertEquals(4, ContactSheet.layout(10, 1, 1).columns());
        assertEquals(3, ContactSheet.layout(10, 1, 1).rows());
    }

    @Test
    void labelsNameTheFrameAndTick() {
        assertEquals("#3 t1234", ContactSheet.label(3, 1234));
    }

    @Test
    void labelsAreDrawnOnABackdrop() {
        Map<Long, Integer> pixels = new HashMap<>();
        ContactSheet.draw((x, y, color) -> pixels.put((long) y << 32 | x, color), 10, 20, "#1");
        // Two glyphs of 3x5 font pixels at scale 2, a font pixel apart, and 2 pixels of padding around.
        int width = 2 * 4 * 2 - 2 + 4;
        int height = 5 * 2 + 4;
        assertEquals(width * height, pixels.size());
        for (int y = 20; y < 20 + height; y++) {
            for (int x = 10; x < 10 + width; x++) {
                int color = pixels.get((long) y << 32 | x);
                assertTrue(color == ContactSheet.TEXT || color == ContactSheet.BACKDROP);
            }
        }
        // The 1's top row is its middle pixel only: font column 1 of the second glyph, which starts at x 10+2+8,
        // on the first row below the padding.
        assertEquals(ContactSheet.TEXT, pixels.get(22L << 32 | 10 + 2 + 8 + 2));
        assertEquals(ContactSheet.BACKDROP, pixels.get(22L << 32 | 10 + 2 + 8));
        Map<Long, Integer> unknown = new HashMap<>();
        ContactSheet.draw((x, y, color) -> unknown.put((long) y << 32 | x, color), 0, 0, "?");
        assertTrue(unknown.values().stream().allMatch(color -> color == ContactSheet.BACKDROP));
    }
}

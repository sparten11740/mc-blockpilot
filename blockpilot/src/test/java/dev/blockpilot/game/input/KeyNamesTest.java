package dev.blockpilot.game.input;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class KeyNamesTest {
    private static final List<String> KNOWN = List.of("left_shift", "right_shift", "left_control", "left_alt", "left",
        "space", "f5", "key.mouse.left");

    @Test
    void underscoresAlsoTryDots() {
        assertEquals(List.of("left_shift", "left.shift"), KeyNames.spellings("left_shift"));
        assertEquals(List.of("left.shift"), KeyNames.spellings("left.shift"));
        assertEquals(List.of("space"), KeyNames.spellings("space"));
    }

    @Test
    void gameNamesBecomeAgentNames() {
        assertEquals("left_shift", KeyNames.agentName("key.keyboard.left.shift"));
        assertEquals("keypad_0", KeyNames.agentName("key.keyboard.keypad.0"));
        assertEquals("key.mouse.left", KeyNames.agentName("key.mouse.left"));
    }

    @Test
    void buttonsAreNamedAsMouseCommandsTakeThemThenByNumber() {
        assertEquals("left", KeyNames.buttonName(0));
        assertEquals("right", KeyNames.buttonName(1));
        assertEquals("middle", KeyNames.buttonName(2));
        assertEquals("4", KeyNames.buttonName(3));
    }

    @Test
    void aMisspellingNamesTheClosestKeys() {
        assertEquals(List.of("left_shift", "left_alt", "left"), KeyNames.closest("leftshift", KNOWN, 3));
        assertEquals(List.of("f5", "left"), KeyNames.closest("f6", KNOWN, 2));
    }

    @Test
    void editDistanceCountsSingleEdits() {
        assertEquals(0, KeyNames.distance("space", "space"));
        assertEquals(1, KeyNames.distance("leftshift", "left_shift"));
        assertEquals(3, KeyNames.distance("", "abc"));
        assertEquals(2, KeyNames.distance("ab", "ba"));
    }
}

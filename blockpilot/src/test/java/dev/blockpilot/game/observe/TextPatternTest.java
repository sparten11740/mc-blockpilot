package dev.blockpilot.game.observe;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TextPatternTest {
    private static boolean matches(String pattern, String text) {
        return TextPattern.compile(pattern).matcher(text).matches();
    }

    @Test
    void starsStandForAnyRunIgnoringCase() {
        assertTrue(matches("*engaged*", "Autopilot ENGAGED!"));
        assertTrue(matches("*gear*refused*", "Gear refused: airspeed too high"));
        assertTrue(matches("auto*", "Autopilot engaged"));
        assertTrue(matches("*", ""));
    }

    @Test
    void theWholeTextMustFit() {
        assertFalse(matches("engaged", "Autopilot engaged"));
        assertFalse(matches("*engage", "Autopilot engaged"));
    }

    @Test
    void everythingElseMatchesItself() {
        assertFalse(matches("a.b", "axb"));
        assertTrue(matches("a.b (x)?", "A.B (X)?"));
        assertTrue(matches("*\\E*", "a \\E b"));
    }
}

package dev.blockpilot.game.load;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ReleaseTest {
    @Test
    void passesStraightOnWithoutDelayWhileNothingIsHeld() {
        Release release = new Release();
        assertEquals(-1, release.hold(100, 0));
        assertFalse(release.holding());
    }

    @Test
    void jitterNeverLetsAPacketOvertakeTheOneAhead() {
        Release release = new Release();
        // Sent at 0, 1 and 2 with delays of 50, 10 and 70: released at 50, 50 and 72.
        assertEquals(50, release.hold(0, 50));
        assertEquals(49, release.hold(1, 10));
        assertEquals(70, release.hold(2, 70));
        assertTrue(release.holding());
    }

    @Test
    void aPacketWithoutDelayWaitsForTheOnesHeldAhead() {
        Release release = new Release();
        release.hold(0, 50);
        assertEquals(40, release.hold(10, 0));
        release.released();
        release.released();
        assertFalse(release.holding());
        assertEquals(-1, release.hold(60, 0));
    }
}

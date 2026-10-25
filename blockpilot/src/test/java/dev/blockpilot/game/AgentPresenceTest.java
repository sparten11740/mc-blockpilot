package dev.blockpilot.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Driven tick by tick, 50 ms apart, as the client does. */
class AgentPresenceTest {
    private final AgentPresence presence = new AgentPresence();
    private long now = 1_000_000;

    private void tick(int sessions, String activity) {
        now += 50;
        presence.update(sessions, activity, now);
    }

    @Test
    void anAgentStaysPresentBetweenOneShotConnections() {
        assertFalse(presence.present(now));
        tick(1, null);
        tick(0, null);
        now += AgentPresence.GRACE_MILLIS - 100;
        assertTrue(presence.present(now));
        now += 100;
        assertFalse(presence.present(now));
    }

    @Test
    void shortActionsInARowDoNotFlicker() {
        // A two-tick server command every second, as an agent polling with server does.
        for (int second = 0; second < 5; second++) {
            tick(1, "server");
            tick(1, "server");
            for (int idle = 0; idle < 18; idle++) {
                tick(0, null);
                assertEquals("server", presence.shownActivity());
            }
        }
    }

    @Test
    void theIndicatorGoesBackToIdleAfterAWhile() {
        tick(1, "goto");
        assertEquals("goto", presence.shownActivity());
        for (int idle = 0; idle < AgentPresence.IDLE_MILLIS / 50 + 1; idle++) {
            tick(1, null);
        }
        assertNull(presence.shownActivity());
    }

    @Test
    void eachTextIsShownForAMoment() {
        tick(1, "server");
        tick(1, "wait");
        assertEquals("server", presence.shownActivity());
        for (int ticks = 0; ticks < AgentPresence.MIN_SHOWN_MILLIS / 50; ticks++) {
            tick(1, "wait");
        }
        assertEquals("wait", presence.shownActivity());
    }
}

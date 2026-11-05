package dev.blockpilot.game.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import dev.blockpilot.api.Action;
import dev.blockpilot.api.Duration;
import dev.blockpilot.api.Result;
import org.junit.jupiter.api.Test;

class TimedStopTest {
    private static final class Counting extends TimedStop {
        private int stops;

        Counting(Duration duration) {
            super(duration);
        }

        @Override
        protected Action stop() {
            stops++;
            return () -> Result.ok();
        }
    }

    @Test
    void itDoesNotStopBeforeTheTimeIsUp() throws Exception {
        Counting timed = new Counting(new Duration(1_000));
        assertNull(timed.tick());
        assertEquals(0, timed.stops);
    }

    @Test
    void itAsksForTheStopOnceAndAnswersWithIt() throws Exception {
        Counting timed = new Counting(new Duration(0));
        assertNotNull(timed.tick());
        assertNotNull(timed.tick());
        assertEquals(1, timed.stops);
    }
}

package dev.blockpilot.game.observe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.junit.jupiter.api.Test;

class TickLogTest {
    private final TickLog<Long> log = new TickLog<>(3, Long::longValue);

    @Test
    void keepsTheLastEntriesOldestFirst() {
        for (long tick = 1; tick <= 5; tick++) {
            log.add(tick);
        }
        assertEquals(List.of(3L, 4L, 5L), log.since(Long.MIN_VALUE));
        assertEquals(List.of(4L, 5L), log.since(4));
    }

    @Test
    void knowsItsOldestTick() {
        assertNull(log.oldest());
        log.add(10L);
        log.add(20L);
        assertEquals(10L, log.oldest());
    }

    @Test
    void saysWhereItStartsOnlyWhenTheWindowLostEntries() {
        log.add(10L);
        log.add(20L);
        log.add(30L);
        assertNull(log.from(Long.MIN_VALUE));
        log.add(40L);
        log.add(50L);
        assertEquals(30L, log.from(Long.MIN_VALUE));
        assertEquals(30L, log.from(20));
        assertNull(log.from(21));
        assertNull(log.from(45));
    }

    @Test
    void clearedItHoldsNothingAndHasDroppedNothing() {
        for (long tick = 1; tick <= 5; tick++) {
            log.add(tick);
        }
        log.clear();
        assertEquals(List.of(), log.since(Long.MIN_VALUE));
        assertNull(log.oldest());
        assertNull(log.from(Long.MIN_VALUE));
        log.add(9L);
        assertEquals(List.of(9L), log.since(Long.MIN_VALUE));
        assertNull(log.from(Long.MIN_VALUE));
    }
}

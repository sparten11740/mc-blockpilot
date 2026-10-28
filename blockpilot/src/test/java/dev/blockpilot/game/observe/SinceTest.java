package dev.blockpilot.game.observe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.blockpilot.api.ArgType;
import dev.blockpilot.api.Args;
import dev.blockpilot.api.CommandException;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SinceTest {
    private static long since(String text, ArgType type) throws CommandException {
        return Since.tick(new Args(Map.of(), Map.of("since", new Args.Value(text, type))), 1000, -1);
    }

    @Test
    void aTickNumberIsTheFirstTick() throws CommandException {
        assertEquals(1200, since("1200", ArgType.INT));
    }

    @Test
    void aDurationCountsBackFromNow() throws CommandException {
        assertEquals(940, since("60t", ArgType.DURATION));
        assertEquals(940, since("3s", ArgType.DURATION));
    }

    @Test
    void withoutSinceTheFallback() throws CommandException {
        assertEquals(-1, Since.tick(new Args(Map.of(), Map.of()), 1000, -1));
    }

    @Test
    void theWindowStartsAtTheAskedTickWhileTheLogHoldsItAll() {
        assertEquals(100, Since.first(100, null, 12959L, 14159));
        assertEquals(14059, Since.first(14059, null, 12959L, 14159));
    }

    @Test
    void theWindowStartsWhereTheLogBeginsWhenItDroppedPartOfIt() {
        assertEquals(12959, Since.first(100, 12959L, 12959L, 14159));
    }

    @Test
    void withoutSinceTheWindowStartsAtTheOldestEntryOrNow() {
        assertEquals(12959, Since.first(Long.MIN_VALUE, null, 12959L, 14159));
        assertEquals(14159, Since.first(Long.MIN_VALUE, null, null, 14159));
    }

    private static Args since(String text, ArgType type, boolean given) {
        return new Args(Map.of(), given ? Map.of("since", new Args.Value(text, type)) : Map.of());
    }

    @Test
    void aBareTickFromBeforeJoiningIsFlagged() {
        assertTrue(Since.predatesJoin(since("100", ArgType.INT, true), 12890L));
    }

    @Test
    void aTickFromThisWorldADurationOrNoSinceIsNot() {
        assertFalse(Since.predatesJoin(since("13000", ArgType.INT, true), 12890L));
        assertFalse(Since.predatesJoin(since("100t", ArgType.DURATION, true), 12890L));
        assertFalse(Since.predatesJoin(since(null, null, false), 12890L));
        assertFalse(Since.predatesJoin(since("100", ArgType.INT, true), null));
    }

    @Test
    void anythingElseIsASyntaxError() {
        assertEquals("syntax", assertThrows(CommandException.class, () -> since("soon", ArgType.TEXT)).code());
    }
}

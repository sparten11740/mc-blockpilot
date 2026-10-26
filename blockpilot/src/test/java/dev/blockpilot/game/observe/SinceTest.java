package dev.blockpilot.game.observe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
    void anythingElseIsASyntaxError() {
        assertEquals("syntax", assertThrows(CommandException.class, () -> since("soon", ArgType.TEXT)).code());
    }
}

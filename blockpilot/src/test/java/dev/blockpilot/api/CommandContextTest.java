package dev.blockpilot.api;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class CommandContextTest {
    @Test
    void aContextWithoutAScriptRefusesAnEnding() {
        CommandContext context = new CommandContext() {
            @Override
            public int sessionId() {
                return 1;
            }

            @Override
            public Position resolve(Position position) {
                return position;
            }
        };
        assertThrows(UnsupportedOperationException.class, () -> context.atEnd(Result::ok));
    }
}

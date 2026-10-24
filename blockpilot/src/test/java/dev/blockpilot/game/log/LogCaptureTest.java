package dev.blockpilot.game.log;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LogCaptureTest {
    @Test
    void condensesNestedExceptions() {
        Exception cause = new IllegalStateException("gear jammed");
        Exception thrown = new RuntimeException("tick failed", cause);
        String text = LogCapture.condensed(thrown);
        assertTrue(text.startsWith("java.lang.RuntimeException: tick failed at dev.blockpilot."), text);
        assertTrue(text.endsWith("| caused by java.lang.IllegalStateException: gear jammed"), text);
        assertTrue(text.split(" at ").length <= 4, text);
    }
}

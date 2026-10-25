package dev.blockpilot.game.log;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.logging.log4j.LogManager;
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

    @Test
    void keepsTheFullTraceOfALoggedErrorForLogDetail() {
        LogCapture.install();
        Exception thrown = new RuntimeException("tick failed", new IllegalStateException("gear jammed"));
        LogManager.getLogger("dev.blockpilot.test").error("build failed", thrown);
        LogCapture.Entry entry = LogCapture.get(LogCapture.mark());
        assertEquals("ERROR", entry.level());
        assertEquals("dev.blockpilot.test", entry.logger());
        assertEquals("build failed", entry.message());
        assertTrue(entry.trace().endsWith("| caused by java.lang.IllegalStateException: gear jammed"), entry.trace());
        assertTrue(entry.fullTrace().contains("Caused by: java.lang.IllegalStateException: gear jammed"),
            entry.fullTrace());
        assertTrue(entry.fullTrace().split("\tat ").length > 4, entry.fullTrace());
    }
}

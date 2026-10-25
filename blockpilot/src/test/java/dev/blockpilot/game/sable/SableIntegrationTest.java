package dev.blockpilot.game.sable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SableIntegrationTest {
    @Test
    void acceptsMajorOneFromMinorSix() {
        assertNull(SableIntegration.versionProblem("1.6.0"));
        assertNull(SableIntegration.versionProblem("1.6"));
        assertNull(SableIntegration.versionProblem("1.12.3-beta.1+mc1.21.1"));
        assertEquals("Sable companion 1.5.9 found, Blockpilot supports 1.x from 1.6",
            SableIntegration.versionProblem("1.5.9"));
        assertEquals("Sable companion 2.0.0 found, Blockpilot supports 1.x from 1.6",
            SableIntegration.versionProblem("2.0.0"));
        assertEquals("Sable companion 0.9 found, Blockpilot supports 1.x from 1.6",
            SableIntegration.versionProblem("0.9"));
        assertEquals("Sable companion unknown found, Blockpilot supports 1.x from 1.6",
            SableIntegration.versionProblem("unknown"));
        assertEquals("no Sable companion found", SableIntegration.versionProblem(null));
    }

    @Test
    void turningOffForgetsTheSubLevelsAndTellsTheListener() {
        List<String> switchedOff = new ArrayList<>();
        SableIntegration integration = new SableIntegration(switchedOff::add);
        SubLevels compat = new SubLevels() {
        };
        integration.on(compat, "1.6.0");
        assertSame(compat, integration.subLevels());
        assertEquals("on: Sable companion 1.6.0", integration.status());
        integration.off("Sable is not installed");
        assertSame(SubLevels.NONE, integration.subLevels());
        assertEquals(List.of("off: Sable is not installed"), switchedOff);
    }
}

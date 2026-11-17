package dev.blockpilot.game.observe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HudLayersTest {
    private boolean hidden;
    private final HudLayers layers = new HudLayers(new HudLayers.Switch() {
        @Override
        public boolean hidden() {
            return hidden;
        }

        @Override
        public void setHidden(boolean value) {
            hidden = value;
        }
    });

    @Test
    void theOptionSetsTheSwitchAndCleanHidesToo() {
        assertEquals("on", layers.option());
        layers.setOption("off");
        assertTrue(hidden);
        assertEquals("off", layers.option());
        layers.setOption("clean");
        assertTrue(hidden);
        assertTrue(layers.clean());
        assertEquals("clean", layers.option());
        layers.setOption("on");
        assertFalse(hidden);
        assertFalse(layers.clean());
    }

    @Test
    void aCaptureHidesUntilItEndsAndReadsAsTheOption() {
        HudLayers.Capture capture = layers.capture(HudLayers.Mode.CLEAN);
        assertTrue(hidden);
        assertTrue(layers.clean());
        assertEquals("on", layers.option(), "a capture isn't the option");
        capture.end();
        assertFalse(hidden);
        assertFalse(layers.clean());
    }

    @Test
    void anOptionSetDuringACaptureHoldsAfterIt() {
        HudLayers.Capture capture = layers.capture(HudLayers.Mode.CLEAN);
        layers.setOption("off");
        assertTrue(hidden);
        assertEquals("off", layers.option());
        capture.end();
        assertTrue(hidden, "off, as the option was set");
        assertFalse(layers.clean());
        assertEquals("off", layers.option());
    }

    @Test
    void f1DuringACaptureIsUndoneOnItsNextTick() {
        HudLayers.Capture capture = layers.capture(HudLayers.Mode.CLEAN);
        hidden = false;
        capture.tick();
        assertTrue(hidden);
        assertTrue(layers.clean());
        capture.end();
    }

    @Test
    void capturesNestAndTheLastPutsTheSwitchBack() {
        HudLayers.Capture outer = layers.capture(HudLayers.Mode.CLEAN);
        HudLayers.Capture inner = layers.capture(HudLayers.Mode.HIDDEN);
        inner.end();
        assertTrue(hidden);
        assertTrue(layers.clean(), "the clean capture still runs");
        outer.end();
        assertFalse(hidden);
        assertFalse(layers.clean());
    }

    @Test
    void anAsIsCaptureLeavesTheHudAndEndingTwiceIsHarmless() {
        hidden = true;
        HudLayers.Capture asIs = layers.capture(HudLayers.Mode.AS_IS);
        asIs.tick();
        asIs.end();
        assertTrue(hidden);
        HudLayers.Capture capture = layers.capture(HudLayers.Mode.HIDDEN);
        capture.end();
        layers.setOption("on");
        capture.end();
        assertFalse(hidden);
    }

    @Test
    void modesComeFromTheHudOption() {
        assertEquals(HudLayers.Mode.HIDDEN, HudLayers.Mode.of("off"));
        assertEquals(HudLayers.Mode.CLEAN, HudLayers.Mode.of("clean"));
        assertEquals(HudLayers.Mode.AS_IS, HudLayers.Mode.of("on"));
        assertEquals(HudLayers.Mode.AS_IS, HudLayers.Mode.of(null));
    }
}

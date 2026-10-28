package dev.blockpilot.game.perf;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SourceFramesTest {
    private static final String LISTENER = "net.minecraft.client.multiplayer.ClientPacketListener";

    @Test
    void theSourceIsTheFirstFrameOutsideTheSkippedPackages() {
        assertFalse(SourceFrames.namesSource("java.util.ArrayList", LISTENER));
        assertFalse(SourceFrames.namesSource("net.minecraft.client.renderer.LevelRenderer", LISTENER));
        assertFalse(SourceFrames.namesSource("net.minecraft.class_761", LISTENER));
        assertFalse(SourceFrames.namesSource("dev.blockpilot.mixin.ParticleEngineMixin", LISTENER));
        assertFalse(SourceFrames.namesSource("org.spongepowered.asm.mixin.Mixins", LISTENER));
        assertTrue(SourceFrames.namesSource("com.example.burner.BurnerBlockEntity", LISTENER));
    }

    @Test
    void thePacketListenerNamesTheServerAsTheSource() {
        assertTrue(SourceFrames.namesSource(LISTENER, LISTENER));
    }
}

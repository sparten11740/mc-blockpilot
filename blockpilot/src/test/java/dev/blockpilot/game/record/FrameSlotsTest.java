package dev.blockpilot.game.record;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FrameSlotsTest {
    private static final long MILLIS = 1_000_000L;

    @Test
    void aSlotShowsTheNewestFrameFinishedByItsTime() {
        // 50 fps: slots at 0, 20 and 40 ms.
        FrameSlots slots = new FrameSlots(50);
        assertEquals(0, slots.replace(0));
        assertEquals(1, slots.replace(10 * MILLIS), "the frame at 0 ms fills the slot at 0 ms");
        assertEquals(0, slots.replace(20 * MILLIS), "the frame at 10 ms is newer than none of the slots' times");
        assertEquals(1, slots.replace(40 * MILLIS), "the frame at 20 ms fills the slot at 20 ms");
        assertEquals(1, slots.end(50 * MILLIS), "the frame at 40 ms fills the slot at 40 ms");
        assertEquals(3, slots.slots());
        assertEquals(0, slots.repeats());
    }

    @Test
    void theFirstSlotFromATimeStartsAtOrAfterIt() {
        assertEquals(0, FrameSlots.firstSlotFrom(0, 20));
        assertEquals(0, FrameSlots.firstSlotFrom(-5 * MILLIS, 20), "before the first frame is the first slot");
        assertEquals(10, FrameSlots.firstSlotFrom(500 * MILLIS, 20), "slot 10 starts at 500 ms");
        assertEquals(11, FrameSlots.firstSlotFrom(500 * MILLIS + 1, 20), "just after a slot's start is the next");
        assertEquals(30, FrameSlots.firstSlotFrom(500 * MILLIS, 60));
        assertEquals(31, FrameSlots.firstSlotFrom(501 * MILLIS, 60));
    }

    @Test
    void renderingSlowerThanTheFrameRateRepeatsFrames() {
        FrameSlots slots = new FrameSlots(60);
        long frames = 0;
        for (long time = 0; time < 1000 * MILLIS; time += 25 * MILLIS) {
            frames += slots.replace(time);
        }
        frames += slots.end(1000 * MILLIS);
        assertEquals(60, frames);
        assertEquals(20, slots.repeats(), "40 frames rendered, so 20 of the 60 repeat");
    }

    @Test
    void aHalfSecondHitchRepeatsAboutThirtyFrames() {
        FrameSlots slots = new FrameSlots(60);
        long time = 0;
        for (int frame = 0; frame < 60; frame++, time += 1_000 * MILLIS / 60) {
            slots.replace(time);
        }
        long before = slots.repeats();
        int held = slots.replace(time + 500 * MILLIS);
        assertTrue(held >= 30 && held <= 31, "held for " + held + " slots");
        assertEquals(held - 1, slots.repeats() - before);
    }

    @Test
    void renderingFasterSkipsFramesWithoutRepeats() {
        FrameSlots slots = new FrameSlots(60);
        long frames = 0;
        for (long time = 0; time < 1000 * MILLIS; time += 5 * MILLIS) {
            frames += slots.replace(time);
        }
        frames += slots.end(1000 * MILLIS);
        assertEquals(60, frames);
        assertEquals(0, slots.repeats());
    }

    @Test
    void theVideoLastsAsLongAsTheRecording() {
        FrameSlots slots = new FrameSlots(30);
        slots.replace(0);
        slots.replace(400 * MILLIS);
        slots.end(2_500 * MILLIS);
        assertEquals(75, slots.slots());
        assertEquals(2_500 * MILLIS, slots.nanos());
    }

    @Test
    void aRecordingStoppedAtOnceStillHasOneFrame() {
        FrameSlots slots = new FrameSlots(60);
        slots.replace(0);
        assertEquals(1, slots.end(0));
        assertEquals(0, new FrameSlots(60).end(MILLIS), "nothing to fill without a frame");
    }
}

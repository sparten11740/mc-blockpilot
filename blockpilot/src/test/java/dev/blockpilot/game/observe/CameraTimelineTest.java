package dev.blockpilot.game.observe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.blockpilot.api.CommandException;
import dev.blockpilot.api.Result;
import dev.blockpilot.protocol.CommandFailure;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CameraTimelineTest {
    private final List<String> started = new ArrayList<>();
    private long ticks;
    private boolean moving;
    private Runnable onMovingAsked = () -> { };
    private final CameraTimeline timeline = new CameraTimeline(new CameraTimeline.Clock() {
        @Override
        public long ticks() {
            return ticks;
        }

        @Override
        public boolean moving() {
            onMovingAsked.run();
            return moving;
        }
    });

    private CameraTimeline.Shot cue(String name) {
        return new CameraTimeline.Cue(() -> {
            started.add(name);
            return () -> Result.ok();
        }, 0);
    }

    private static CameraTimeline.Shot failing(RuntimeException failure) {
        return new CameraTimeline.Cue(() -> {
            throw failure;
        }, 0);
    }

    private static CameraTimeline.Shot failing(CommandException failure) {
        return new CameraTimeline.Cue(() -> {
            throw failure;
        }, 0);
    }

    private void tick(int times) {
        for (int index = 0; index < times; index++) {
            ticks++;
            timeline.tick();
        }
    }

    @Test
    void shotsPlayInTurnAndAHoldLastsItsTicks() throws CommandException {
        timeline.play(List.of(cue("at"), new CameraTimeline.Hold(3), cue("move")));
        assertEquals(List.of("at"), started);
        tick(1);
        tick(2);
        assertEquals(List.of("at"), started, "the hold still runs");
        tick(1);
        assertEquals(List.of("at", "move"), started);
        moving = true;
        tick(5);
        assertTrue(timeline.playing(), "the move still runs");
        moving = false;
        tick(1);
        assertFalse(timeline.playing());
        assertNull(timeline.takeFailure());
    }

    @Test
    void aFirstShotThatFailsLeavesNothingPlaying() {
        assertThrows(CommandException.class, () -> timeline.play(List.of(
            failing(new CommandException("syntax", "over takes 1t or more")), cue("at"))));
        assertFalse(timeline.playing());
        tick(3);
        assertEquals(List.of(), started);
    }

    @Test
    void aLaterFailureEndsThePlayAndIsToldOnce() throws CommandException {
        timeline.play(List.of(new CameraTimeline.Hold(1), failing(new CommandException("no_entity", "e7")),
            cue("at")));
        tick(1);
        assertFalse(timeline.playing());
        Result failure = timeline.takeFailure();
        assertEquals("no_entity", failure.code());
        assertEquals("e7", failure.argument());
        assertNull(timeline.takeFailure());
        tick(3);
        assertEquals(List.of(), started);
    }

    @Test
    void aRuntimeErrorEndsThePlayWithoutReachingTheTick() throws CommandException {
        timeline.play(List.of(new CameraTimeline.Hold(1), failing(new IllegalStateException("broken"))));
        tick(1);
        assertFalse(timeline.playing());
        assertEquals("internal", timeline.takeFailure().code());
        timeline.play(List.of(new CameraTimeline.Hold(1), failing(new CommandFailure("sable", "lookup failed"))));
        tick(1);
        assertEquals("sable", timeline.takeFailure().code());
    }

    @Test
    void aStopForgetsWhyTheLastPlayFailed() throws CommandException {
        timeline.play(List.of(new CameraTimeline.Hold(1), failing(new CommandException("no_entity", "e7"))));
        tick(1);
        timeline.stop();
        assertNull(timeline.takeFailure());
    }

    @Test
    void aStopBetweenTheTickAndTheShotStartsNothing() throws CommandException {
        timeline.play(List.of(cue("at"), cue("move")));
        onMovingAsked = timeline::stop;
        tick(1);
        assertEquals(List.of("at"), started);
        assertFalse(timeline.playing());
    }
}

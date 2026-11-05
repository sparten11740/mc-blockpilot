package dev.blockpilot.game.common;

import dev.blockpilot.api.Action;
import dev.blockpilot.api.CommandException;
import dev.blockpilot.api.Duration;
import dev.blockpilot.api.Result;

/**
 * Waits for a duration on the wall clock, a tick counting 50 ms, then does what stops the thing it times. Cancelled
 * before then, it hasn't asked for that, so what it times keeps running and nothing measured is lost.
 */
public abstract class TimedStop implements Action {
    private static final long NANOS_PER_TICK = 50_000_000L;

    private final long endNanos;
    private Action stop;

    protected TimedStop(Duration duration) {
        endNanos = System.nanoTime() + duration.ticks() * NANOS_PER_TICK;
    }

    /** The action that stops it and answers, asked for once the time is up. */
    protected abstract Action stop() throws CommandException;

    @Override
    public final Result tick() throws CommandException {
        if (stop == null) {
            if (System.nanoTime() < endNanos) {
                return null;
            }
            stop = stop();
        }
        return stop.tick();
    }
}

package dev.blockpilot.api;

/**
 * A command that takes game ticks to finish. Blockpilot calls {@link #tick} once per tick on the thread the command
 * runs on until it reports a result; {@link #cancel} is called instead when it is stopped, times out or its session
 * closes.
 */
public interface Action {
    /** Advances the action by one tick: {@code null} while it runs, its result when it is done. */
    Result tick() throws CommandException;

    /** Undoes any held state (pressed keys, movement); called at most once, instead of further ticks. */
    default void cancel() {
    }

    /** A short name for the HUD indicator and logs, e.g. {@code goto}. */
    default String label() {
        return getClass().getSimpleName();
    }
}

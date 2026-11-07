package dev.blockpilot.api;

/** What a command handler can ask about the request it serves. */
public interface CommandContext {
    /** The session the command came from. */
    int sessionId();

    /** The absolute position, resolving {@code ~} positions against the player's block position. */
    Position resolve(Position position) throws CommandException;

    /**
     * Runs the action when the script this command runs in ends, however it ends (its last step, an error that stops
     * it, {@code stop} or real input), on the client thread and before the script's reply, after the endings given
     * before it. Its result arrives in the reply as an event named after the action's label; one that hasn't
     * answered after 60 s of game time at 20 ticks a second is cancelled and answers {@code err timeout}. Where there
     * is no script, it throws.
     */
    default void atEnd(Action ending) {
        throw new UnsupportedOperationException("there is no script to end");
    }
}

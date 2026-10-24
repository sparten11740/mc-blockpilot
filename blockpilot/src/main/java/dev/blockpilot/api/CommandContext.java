package dev.blockpilot.api;

/** What a command handler can ask about the request it serves. */
public interface CommandContext {
    /** The session the command came from. */
    int sessionId();

    /** The absolute position, resolving {@code ~} positions against the player's block position. */
    Position resolve(Position position) throws CommandException;
}

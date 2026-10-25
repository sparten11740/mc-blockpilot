package dev.blockpilot.protocol;

import dev.blockpilot.api.CommandException;
import dev.blockpilot.api.CommandSpec;
import dev.blockpilot.api.Position;

/** What the engine needs from the game, so the engine itself stays free of Minecraft classes. */
public interface GameBridge {
    /** Runs the task on the client thread as soon as possible. */
    void runOnClient(Runnable task);

    /** Runs the task on the integrated server thread; at once when already there or when no server runs. */
    void runOnServer(Runnable task);

    /** False while the game is still loading its resources; queued actions wait until then, queries still run. */
    boolean ready();

    /** Null when the command may run now, otherwise the error code that stops it (e.g. {@code not_in_world}). */
    String gate(CommandSpec spec);

    /** Resolves a {@code ~} position against the player's block position. */
    Position resolve(Position position) throws CommandException;

    /** The tick replies use: the integrated server's game time in singleplayer, else the client's; -1 if none. */
    long gameTime();

    /** Lets go of every key, button and movement an agent holds. */
    void releaseInputs();

    /** Lets go of what this session's commands hold, e.g. when its script fails or it disconnects. */
    void releaseInputs(int sessionId);
}

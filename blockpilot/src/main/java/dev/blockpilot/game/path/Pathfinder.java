package dev.blockpilot.game.path;

import dev.blockpilot.api.Action;
import dev.blockpilot.api.Position;
import java.util.function.Supplier;

/**
 * The port every way of walking the player plugs into: Blockpilot's own pathfinder, or another mod's behind an adapter.
 * Commands depend on this, never on a backend.
 */
public interface Pathfinder {
    /** The name a reply gives as {@code by=} when this pathfinder walked; the built-in one is left out. */
    String name();

    /** Whether it can walk now: its mod is loaded and a world gives it a player. */
    boolean available();

    /**
     * An action that brings the player within {@code near} blocks of the target, an absolute position read each tick
     * (null once the target is gone), answering {@code ok} with {@code took=}, or {@code err no_path} or {@code err
     * stuck}.
     */
    Action walk(int session, Supplier<Position> target, double near);
}

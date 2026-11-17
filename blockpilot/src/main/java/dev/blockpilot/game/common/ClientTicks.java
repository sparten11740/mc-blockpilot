package dev.blockpilot.game.common;

/**
 * Client ticks since the game started, rising by one at the end of each: the client tick sample rows carry, the clock
 * camera moves run on, and what frames' drawn times count in. Unlike {@link GameClock} it goes on through tick freeze.
 */
public final class ClientTicks {
    private static volatile long count;

    private ClientTicks() {
    }

    public static long now() {
        return count;
    }

    /** Once per client tick, at its end, before what runs on it. Client thread. */
    public static void advance() {
        count++;
    }
}

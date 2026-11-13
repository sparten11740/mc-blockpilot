package dev.blockpilot.game;

import net.minecraft.server.MinecraftServer;
//? if <1.21 {
/*import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
*///?}

/**
 * The integrated server's tick state as tick control reads and sets it: the game's tick rate manager, or on 1.20.1,
 * which has none, Blockpilot's port of it. The port's mixins ask {@code runsNormally} and {@code frozen(Entity)} what
 * to skip, and {@code millisThisTick} how long a tick takes.
 */
public final class ServerTicking {
    //? if <1.21 {
    /*private static final double NANOS = 1_000_000_000d;
    private static volatile boolean frozen;
    private static volatile float rate = 20;
    private static volatile int stepsLeft;
    private static volatile boolean runsNormally = true;
    // The part of a millisecond earlier ticks were owed, so a rate that isn't a whole number of milliseconds a tick
    // holds on average.
    private static double owedNanos;
    *///?}

    private ServerTicking() {
    }

    static boolean frozen(MinecraftServer server) {
        //? if >=1.21 {
        return server.tickRateManager().isFrozen();
        //?} else {
        /*return frozen;
        *///?}
    }

    static float rate(MinecraftServer server) {
        //? if >=1.21 {
        return server.tickRateManager().tickrate();
        //?} else {
        /*return rate;
        *///?}
    }

    static void freeze(MinecraftServer server, boolean freeze) {
        //? if >=1.21 {
        server.tickRateManager().setFrozen(freeze);
        //?} else {
        /*frozen = freeze;
        if (!freeze) {
            stepsLeft = 0;
        }
        *///?}
    }

    static void rate(MinecraftServer server, float ticksPerSecond) {
        //? if >=1.21 {
        server.tickRateManager().setTickRate(ticksPerSecond);
        //?} else {
        /*rate = ticksPerSecond;
        *///?}
    }

    /** Lets that many whole ticks run while frozen. */
    static void step(MinecraftServer server, int ticks) {
        //? if >=1.21 {
        server.tickRateManager().stepGameIfPaused(ticks);
        //?} else {
        /*stepsLeft = ticks;
        *///?}
    }

    static boolean stepping(MinecraftServer server) {
        //? if >=1.21 {
        return server.tickRateManager().isSteppingForward();
        //?} else {
        /*return stepsLeft > 0;
        *///?}
    }

    //? if <1.21 {
    /*// At the start of each server tick: whether the game runs this tick, or only players and what they ride.
    public static void startTick() {
        runsNormally = !frozen || stepsLeft > 0;
        if (frozen && stepsLeft > 0) {
            stepsLeft--;
        }
    }

    public static boolean runsNormally() {
        return runsNormally;
    }

    // Whether the entity waits out this tick: all but players and what they ride, while the game doesn't run.
    public static boolean frozen(Entity entity) {
        if (runsNormally || entity instanceof Player) {
            return false;
        }
        for (Entity passenger : entity.getIndirectPassengers()) {
            if (passenger instanceof Player) {
                return false;
            }
        }
        return true;
    }

    // The milliseconds a client tick takes: the rate's, as the client follows its integrated server.
    public static float clientMillisPerTick() {
        return 1000f / rate;
    }

    // The milliseconds a tick takes at the rate, at least one.
    public static long millisPerTick() {
        return Math.max(1, Math.round(1000 / rate));
    }

    // The milliseconds the server loop waits for this tick, owing the remainder to the next.
    public static synchronized long millisThisTick() {
        owedNanos += NANOS / rate;
        long millis = (long) (owedNanos / 1_000_000);
        owedNanos -= millis * 1_000_000d;
        return millis;
    }
    *///?}
}

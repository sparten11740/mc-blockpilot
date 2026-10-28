package dev.blockpilot.game.load;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.LockSupport;
import java.util.function.DoubleSupplier;

/**
 * Load an agent adds on purpose: idle time in every integrated-server tick, in every frame, and for every packet on
 * the client's connection. Set on the client thread; read on the server, render and network threads.
 */
public final class Load {
    public static final int MAX_MS = 1000;
    public static final int MAX_NET_MS = 2000;

    /** A load in milliseconds, and how far each draw may stray from it either way. */
    public record Setting(int ms, int jitter) {
        public static final Setting OFF = new Setting(0, 0);

        /** Nanoseconds drawn uniformly within the jitter around the load, from {@code unit} in [0, 1). */
        long draw(DoubleSupplier unit) {
            return Math.max(0, Math.round((ms + (unit.getAsDouble() * 2 - 1) * jitter) * 1e6));
        }

        @Override
        public String toString() {
            return ms + (jitter > 0 ? "±" + jitter : "") + "ms";
        }
    }

    private static volatile Setting server = Setting.OFF;
    private static volatile Setting client = Setting.OFF;
    private static volatile Setting net = Setting.OFF;

    private Load() {
    }

    public static Setting server() {
        return server;
    }

    public static Setting client() {
        return client;
    }

    public static Setting net() {
        return net;
    }

    static void set(String target, Setting setting) {
        switch (target) {
            case "server" -> server = setting;
            case "client" -> client = setting;
            default -> net = setting;
        }
    }

    /** Ends every load; returns what was set, e.g. {@code server=40±10ms net=80ms}, or empty. */
    public static String off() {
        List<String> ended = new ArrayList<>();
        if (server.ms() > 0) {
            ended.add("server=" + server);
        }
        if (client.ms() > 0) {
            ended.add("client=" + client);
        }
        if (net.ms() > 0) {
            ended.add("net=" + net);
        }
        server = Setting.OFF;
        client = Setting.OFF;
        net = Setting.OFF;
        return String.join(" ", ended);
    }

    /** At the start of an integrated-server tick's work, on the server thread, inside the time the tick tallies. */
    public static void serverTick() {
        idle(server);
    }

    /** At the start of a frame, on the render thread. */
    public static void frame() {
        idle(client);
    }

    /** Draws a delay for one packet, in nanoseconds. */
    static long netDelay() {
        return net.draw(ThreadLocalRandom.current()::nextDouble);
    }

    private static void idle(Setting setting) {
        if (setting.ms() == 0) {
            return;
        }
        long nanos = setting.draw(ThreadLocalRandom.current()::nextDouble);
        long deadline = System.nanoTime() + nanos;
        // Parking may end early, so it parks again for what is left.
        for (long left = nanos; left > 0; left = deadline - System.nanoTime()) {
            LockSupport.parkNanos(left);
        }
    }
}

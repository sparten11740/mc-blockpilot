package dev.blockpilot.game.perf;

import com.google.gson.JsonObject;
import java.util.Map;

/**
 * Performance capture. Instrumented code checks {@link #active} and does nothing else while no capture runs. While one
 * runs, these hand what they're told to its {@link PerfCapture}.
 */
public final class Perf {
    /** The one check instrumented code makes when no capture runs. */
    public static volatile boolean active;

    /** A block change counted under its block, block entity type and sub-level (both may be null). */
    record Change(String block, String blockEntity, String sub) {
    }

    /** A block position in a dimension, named as {@code status} names it (e.g. overworld). */
    record Spot(String dimension, int x, int y, int z) {
    }

    /** The running capture, or the last one; set before {@link #active}, so whoever sees that true sees this. */
    private static volatile PerfCapture current;
    private static volatile int capture;

    private Perf() {
    }

    public static synchronized String name() {
        return current == null ? null : current.name();
    }

    /** Whether the running capture samples stacks and profiles sections. */
    public static synchronized boolean profiled() {
        return current != null && current.profiled();
    }

    /** Which capture runs or ran last, so per-capture caches elsewhere know when to start over. */
    public static int capture() {
        return capture;
    }

    public static double seconds() {
        return active ? current.seconds() : 0;
    }

    public static synchronized void start(String captureName) {
        start(captureName, null);
    }

    /** Starts a capture; with a server thread, also samples its stack (a profiled capture). */
    public static synchronized void start(String captureName, Thread server) {
        capture++;
        current = new PerfCapture(captureName, server);
        active = true;
    }

    // Hooks called by the instrumentation mixins. Each returns at once unless a capture is active.

    /** At the start and the end of each integrated server tick. */
    public static void serverTicking(boolean ticking) {
        if (active) {
            current.serverTicking(ticking);
        }
    }

    public static void blockEntityStart(boolean client) {
        if (active) {
            current.blockEntityStart(client);
        }
    }

    public static void blockEntityEnd(boolean client, String type) {
        if (active) {
            current.blockEntityEnd(client, type);
        }
    }

    public static void entityStart(boolean client) {
        if (active) {
            current.entityStart(client);
        }
    }

    public static void entityEnd(boolean client, String type) {
        if (active) {
            current.entityEnd(client, type);
        }
    }

    public static void renderStart() {
        if (active) {
            current.renderStart();
        }
    }

    public static void renderEnd(String type) {
        if (active) {
            current.renderEnd(type);
        }
    }

    /** Ends an earlier step of a render that {@link #renderEnd} completes. */
    public static void renderStepEnd(String type) {
        if (active) {
            current.renderStepEnd(type);
        }
    }

    public static void entityRenderStart() {
        if (active) {
            current.entityRenderStart();
        }
    }

    public static void entityRenderEnd(String type) {
        if (active) {
            current.entityRenderEnd(type);
        }
    }

    /** Ends an earlier step of an entity render that {@link #entityRenderEnd} completes. */
    public static void entityRenderStepEnd(String type) {
        if (active) {
            current.entityRenderStepEnd(type);
        }
    }

    public static void particleTickStart() {
        if (active) {
            current.particleTickStart();
        }
    }

    public static void particleTickEnd(String type) {
        if (active) {
            current.particleTickEnd(type);
        }
    }

    public static void particleRendered(String type, long nanos) {
        if (active) {
            current.particleRendered(type, nanos);
        }
    }

    /** At the start of a render-level stage, before its other handlers (NeoForge). */
    public static void stageStart() {
        if (active) {
            current.stageStart();
        }
    }

    /** At the end of a render-level stage, after its other handlers. */
    public static void stageEnd(String stage) {
        if (active) {
            current.stageEnd(stage);
        }
    }

    public static void sectionRebuilt() {
        if (active) {
            current.sectionRebuilt();
        }
    }

    /** A block change the server marks for sending to clients, on the server thread. */
    static void blockChanged(Change change, Spot spot) {
        if (active) {
            current.blockChanged(change, spot);
        }
    }

    /** Counts a particle spawned (on the client) or sent (by the server); true when this one is to be traced. */
    public static boolean traceSpawn(String type, boolean server) {
        return active && current.traceSpawn(type, server);
    }

    /** A traced particle of the type and where it came from. */
    public static void spawnedBy(String type, String mod, String className, String via) {
        if (active) {
            current.spawnedBy(type, mod, className, via);
        }
    }

    public static void packet(boolean toClient, String type) {
        if (active) {
            current.packet(toClient, type);
        }
    }

    public static void packetBytes(boolean toClient, String type, long bytes) {
        if (active) {
            current.packetBytes(toClient, type, bytes);
        }
    }

    /** Once per frame on the client thread. */
    public static void frame() {
        if (active) {
            current.frame();
        }
    }

    /** Once per client tick: samples heap use. */
    public static void clientTick() {
        if (active) {
            current.clientTick();
        }
    }

    /** A chunk became full in a server level, read from disk or generated; on the server thread. */
    public static void chunkLoaded() {
        if (active) {
            current.chunkLoaded();
        }
    }

    public static void chunkGenerated() {
        if (active) {
            current.chunkGenerated();
        }
    }

    public static void chunkSent() {
        if (active) {
            current.chunkSent();
        }
    }

    public static void entityAdded() {
        if (active) {
            current.entityAdded();
        }
    }

    /**
     * Once per server tick, with the duration of the last completed tick and the game tick it ended on. That tick is
     * the one before the hook's, so its counts are those this hook held back last time.
     */
    public static void serverTick(long lastTickNanos, long gameTick) {
        if (active) {
            current.serverTick(lastTickNanos, gameTick);
        }
    }

    /**
     * Ends the capture and builds its summary. The counts of loaded block entities and entities and of live
     * particles are filled in by the caller, which can reach the game.
     */
    public static synchronized JsonObject stop(JsonObject serverCounts, Map<String, Integer> liveParticles) {
        active = false;
        return current.stop(serverCounts, liveParticles);
    }

    static double round(double value) {
        return Math.round(value * 100) / 100.0;
    }
}

package dev.blockpilot.game.perf;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Performance capture. Instrumented code checks {@link #active} and does nothing else while no capture runs. While
 * one runs, timings are added per type: server-side data only on the server thread, client-side data only on the
 * client thread, network data from any thread through concurrent counters.
 */
public final class Perf {
    /** The one check instrumented code makes when no capture runs. */
    public static volatile boolean active;

    /** Total nanoseconds and calls per type; locked, since the summary is built on another thread. */
    static final class Timings {
        private final Map<String, long[]> byType = new HashMap<>();

        synchronized void add(String type, long nanos) {
            long[] entry = byType.computeIfAbsent(type, key -> new long[2]);
            entry[0] += nanos;
            entry[1]++;
        }

        synchronized long calls() {
            return byType.values().stream().mapToLong(entry -> entry[1]).sum();
        }

        /** Types by total time, highest first: type, total_ms, ms per unit (tick or frame), calls. */
        synchronized JsonArray ranked(double units, String perUnit) {
            List<Map.Entry<String, long[]>> entries = new ArrayList<>(byType.entrySet());
            entries.sort((a, b) -> Long.compare(b.getValue()[0], a.getValue()[0]));
            JsonArray array = new JsonArray();
            for (Map.Entry<String, long[]> entry : entries) {
                JsonObject object = new JsonObject();
                object.addProperty("type", entry.getKey());
                double totalMillis = entry.getValue()[0] / 1e6;
                object.addProperty("total_ms", round(totalMillis));
                object.addProperty(perUnit, round(units <= 0 ? 0 : totalMillis / units));
                object.addProperty("calls", entry.getValue()[1]);
                array.add(object);
            }
            return array;
        }
    }

    /** Packets and bytes per type. */
    static final class Traffic {
        private final Map<String, LongAdder[]> byType = new ConcurrentHashMap<>();

        void add(String type, long bytes) {
            LongAdder[] entry = byType.computeIfAbsent(type, key -> new LongAdder[] {new LongAdder(), new LongAdder()});
            entry[0].increment();
            entry[1].add(bytes);
        }

        void addBytes(String type, long bytes) {
            byType.computeIfAbsent(type, key -> new LongAdder[] {new LongAdder(), new LongAdder()})[1].add(bytes);
        }

        JsonArray ranked(double seconds) {
            List<Map.Entry<String, LongAdder[]>> entries = new ArrayList<>(byType.entrySet());
            entries.sort((a, b) -> Long.compare(b.getValue()[0].sum(), a.getValue()[0].sum()));
            JsonArray array = new JsonArray();
            for (Map.Entry<String, LongAdder[]> entry : entries) {
                JsonObject object = new JsonObject();
                object.addProperty("type", entry.getKey());
                long count = entry.getValue()[0].sum();
                object.addProperty("count", count);
                object.addProperty("per_s", round(seconds <= 0 ? 0 : count / seconds));
                object.addProperty("bytes", entry.getValue()[1].sum());
                array.add(object);
            }
            return array;
        }
    }

    private static String name;
    private static long startNanos;
    private static Timings serverBlockEntities = new Timings();
    private static Timings serverEntities = new Timings();
    private static Timings clientBlockEntities = new Timings();
    private static Timings clientEntities = new Timings();
    private static Timings renderers = new Timings();
    private static List<Double> tickMillis = Collections.synchronizedList(new ArrayList<>());
    private static List<Double> frameMillis = Collections.synchronizedList(new ArrayList<>());
    private static long lastFrame;
    private static final AtomicLong SECTION_REBUILDS = new AtomicLong();
    private static Traffic clientbound = new Traffic();
    private static Traffic serverbound = new Traffic();
    private static long clientTicks;
    private static long heapStart;
    private static long heapPeak;
    private static long gcCountStart;
    private static long gcTimeStart;

    // Start times of the call in progress, per thread (none of the timed calls nest within their own kind).
    private static long serverBlockEntityStart;
    private static long clientBlockEntityStart;
    private static long serverEntityStart;
    private static long clientEntityStart;
    private static final Deque<Long> RENDER_STARTS = new ArrayDeque<>();

    private Perf() {
    }

    public static synchronized String name() {
        return name;
    }

    public static double seconds() {
        return active ? (System.nanoTime() - startNanos) / 1e9 : 0;
    }

    public static synchronized void start(String captureName) {
        name = captureName;
        serverBlockEntities = new Timings();
        serverEntities = new Timings();
        clientBlockEntities = new Timings();
        clientEntities = new Timings();
        renderers = new Timings();
        tickMillis = Collections.synchronizedList(new ArrayList<>());
        frameMillis = Collections.synchronizedList(new ArrayList<>());
        lastFrame = 0;
        SECTION_REBUILDS.set(0);
        clientbound = new Traffic();
        serverbound = new Traffic();
        clientTicks = 0;
        RENDER_STARTS.clear();
        heapStart = usedHeap();
        heapPeak = heapStart;
        gcCountStart = gcCount();
        gcTimeStart = gcTime();
        startNanos = System.nanoTime();
        active = true;
    }

    // Hooks called by the instrumentation mixins. Each returns at once unless a capture is active.

    public static void blockEntityStart(boolean client) {
        if (!active) {
            return;
        }
        if (client) {
            clientBlockEntityStart = System.nanoTime();
        } else {
            serverBlockEntityStart = System.nanoTime();
        }
    }

    public static void blockEntityEnd(boolean client, String type) {
        if (!active) {
            return;
        }
        long start;
        if (client) {
            start = clientBlockEntityStart;
            clientBlockEntityStart = 0;
        } else {
            start = serverBlockEntityStart;
            serverBlockEntityStart = 0;
        }
        // A call that began before the capture started has no start time and is not counted.
        if (start != 0) {
            (client ? clientBlockEntities : serverBlockEntities).add(type, System.nanoTime() - start);
        }
    }

    public static void entityStart(boolean client) {
        if (!active) {
            return;
        }
        if (client) {
            clientEntityStart = System.nanoTime();
        } else {
            serverEntityStart = System.nanoTime();
        }
    }

    public static void entityEnd(boolean client, String type) {
        if (!active) {
            return;
        }
        long start;
        if (client) {
            start = clientEntityStart;
            clientEntityStart = 0;
        } else {
            start = serverEntityStart;
            serverEntityStart = 0;
        }
        if (start != 0) {
            (client ? clientEntities : serverEntities).add(type, System.nanoTime() - start);
        }
    }

    public static void renderStart() {
        if (active) {
            RENDER_STARTS.push(System.nanoTime());
        }
    }

    public static void renderEnd(String type) {
        if (active && !RENDER_STARTS.isEmpty()) {
            renderers.add(type, System.nanoTime() - RENDER_STARTS.pop());
        }
    }

    public static void sectionRebuilt() {
        if (active) {
            SECTION_REBUILDS.incrementAndGet();
        }
    }

    public static void packet(boolean toClient, String type) {
        if (active) {
            (toClient ? clientbound : serverbound).add(type, 0);
        }
    }

    public static void packetBytes(boolean toClient, String type, long bytes) {
        if (active) {
            (toClient ? clientbound : serverbound).addBytes(type, bytes);
        }
    }

    /** Once per frame on the client thread. */
    public static void frame() {
        if (!active) {
            return;
        }
        long now = System.nanoTime();
        if (lastFrame != 0) {
            frameMillis.add((now - lastFrame) / 1e6);
        }
        lastFrame = now;
    }

    /** Once per client tick: samples heap use. */
    public static void clientTick() {
        if (active) {
            clientTicks++;
            heapPeak = Math.max(heapPeak, usedHeap());
        }
    }

    /** Once per server tick, with the duration of the last completed tick. */
    public static void serverTick(long lastTickNanos) {
        if (active && lastTickNanos > 0) {
            tickMillis.add(lastTickNanos / 1e6);
        }
    }

    /**
     * Ends the capture and builds its summary. The counts of loaded block entities and entities are filled in by
     * the caller, which can reach the server.
     */
    public static synchronized JsonObject stop(JsonObject serverCounts) {
        active = false;
        double seconds = (System.nanoTime() - startNanos) / 1e9;
        JsonObject summary = new JsonObject();
        summary.addProperty("name", name);
        summary.addProperty("duration_s", round(seconds));

        double[] ticks = toArray(tickMillis);
        JsonObject server = new JsonObject();
        if (ticks.length > 0) {
            JsonObject mspt = new JsonObject();
            mspt.addProperty("avg", round(Stats.average(ticks)));
            mspt.addProperty("p95", round(Stats.percentile(ticks, 0.95)));
            mspt.addProperty("max", round(Stats.max(ticks)));
            server.add("mspt", mspt);
            server.addProperty("tps", round(ticks.length / seconds));
            server.addProperty("ticks", ticks.length);
            server.add("block_entities", serverBlockEntities.ranked(ticks.length, "ms_per_tick"));
            server.add("entities", serverEntities.ranked(ticks.length, "ms_per_tick"));
            if (serverCounts != null) {
                server.add("loaded", serverCounts);
            }
        }
        summary.add("server", server);

        double[] frames = toArray(frameMillis);
        JsonObject client = new JsonObject();
        JsonObject fps = new JsonObject();
        fps.addProperty("avg", round(frames.length == 0 ? 0 : 1000 / Stats.average(frames)));
        fps.addProperty("low_1pct", round(Stats.lowFps(frames, 0.01)));
        client.add("fps", fps);
        JsonObject frameTime = new JsonObject();
        frameTime.addProperty("avg", round(Stats.average(frames)));
        frameTime.addProperty("p95", round(Stats.percentile(frames, 0.95)));
        frameTime.addProperty("max", round(Stats.max(frames)));
        client.add("frame_ms", frameTime);
        client.addProperty("frames", frames.length);
        client.add("block_entity_renderers", renderers.ranked(frames.length, "ms_per_frame"));
        client.addProperty("ticks", clientTicks);
        client.add("block_entity_ticks", clientBlockEntities.ranked(clientTicks, "ms_per_tick"));
        client.add("entities", clientEntities.ranked(clientTicks, "ms_per_tick"));
        JsonObject sections = new JsonObject();
        sections.addProperty("rebuilds", SECTION_REBUILDS.get());
        sections.addProperty("per_s", round(SECTION_REBUILDS.get() / Math.max(seconds, 0.001)));
        client.add("sections", sections);
        JsonObject heap = new JsonObject();
        heap.addProperty("start_mb", heapStart / (1 << 20));
        heap.addProperty("end_mb", usedHeap() / (1 << 20));
        heap.addProperty("peak_mb", heapPeak / (1 << 20));
        client.add("heap", heap);
        JsonObject gc = new JsonObject();
        gc.addProperty("count", gcCount() - gcCountStart);
        gc.addProperty("time_ms", gcTime() - gcTimeStart);
        client.add("gc", gc);
        summary.add("client", client);

        JsonObject network = new JsonObject();
        network.add("clientbound", clientbound.ranked(seconds));
        network.add("serverbound", serverbound.ranked(seconds));
        summary.add("network", network);
        summary.addProperty("instrumented_calls", serverBlockEntities.calls() + serverEntities.calls()
            + clientBlockEntities.calls() + clientEntities.calls() + renderers.calls());
        return summary;
    }

    private static double[] toArray(List<Double> values) {
        synchronized (values) {
            return values.stream().mapToDouble(Double::doubleValue).toArray();
        }
    }

    static double round(double value) {
        return Math.round(value * 100) / 100.0;
    }

    private static long usedHeap() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private static long gcCount() {
        long total = 0;
        for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
            total += Math.max(0, bean.getCollectionCount());
        }
        return total;
    }

    private static long gcTime() {
        long total = 0;
        for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
            total += Math.max(0, bean.getCollectionTime());
        }
        return total;
    }
}

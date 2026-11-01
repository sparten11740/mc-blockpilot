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
import java.util.stream.IntStream;

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

        /** Time from a step of a call that is counted when it completes. */
        synchronized void addTime(String type, long nanos) {
            byType.computeIfAbsent(type, key -> new long[2])[0] += nanos;
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

    /** A block change counted under its block, block entity type and sub-level (both may be null). */
    record Change(String block, String blockEntity, String sub) {
    }

    /** A block position in a dimension, named as {@code status} names it (e.g. overworld). */
    record Spot(String dimension, int x, int y, int z) {
    }

    /** The changes at one position: how many, the block it last changed to and its sub-level (null when none). */
    static final class SpotChanges {
        private long count;
        private String block;
        private String sub;
    }

    /** Code that spawned a particle: its mod and class (null when unknown) and how the particle came. */
    record Spawner(String mod, String className, String via) {
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
    /** Whether the integrated server is inside a tick, as stack samples need to know; kept while a capture runs. */
    private static volatile boolean serverTicking;
    /** Samples the server thread in a profiled capture, else null. */
    private static StackSampler sampler;
    private static volatile int capture;
    private static long startNanos;
    private static Timings serverBlockEntities = new Timings();
    private static Timings serverEntities = new Timings();
    private static Timings clientBlockEntities = new Timings();
    private static Timings clientEntities = new Timings();
    private static Timings renderers = new Timings();
    private static Timings entityRenderers = new Timings();
    private static Timings particleTicks = new Timings();
    private static Timings particleRenders = new Timings();
    private static Timings renderStages = new Timings();
    /** The tick time the server needs for 20 ticks per second; longer ticks are spikes. */
    static final double SPIKE_MS = 50;
    static final int MAX_SPIKES = 20;
    static final int MAX_BLOCK_CHANGES = 20;
    static final int MAX_BLOCK_CHANGE_POSITIONS = 50;
    /** The positions a capture counts block changes at; changes at further ones count only as untracked. */
    static final int MAX_TRACKED_POSITIONS = 65_536;
    private static final String OVERWORLD = "overworld";
    /** The first spawn of each particle type and every this many after it are traced to their source. */
    static final int SOURCE_SAMPLING = 32;
    static final int SOURCES_PER_TYPE = 3;

    private static List<Double> tickMillis = Collections.synchronizedList(new ArrayList<>());
    /** The game tick each of {@link #tickMillis} ended on. */
    private static List<Long> tickLabels = Collections.synchronizedList(new ArrayList<>());
    /** What the server did in each of {@link #tickMillis}: indexed by the {@code WORK_} constants. */
    private static List<int[]> tickWorks = Collections.synchronizedList(new ArrayList<>());
    static final int WORK_CHUNKS_FULL = 0;
    static final int WORK_CHUNKS_GENERATED = 1;
    static final int WORK_CHUNKS_SENT = 2;
    static final int WORK_ENTITIES_ADDED = 3;
    /** Server-thread counts since the last end of tick, and those of the tick that ended last. */
    private static int[] tickWork = new int[4];
    private static int[] endedTickWork = new int[4];
    private static List<Double> frameMillis = Collections.synchronizedList(new ArrayList<>());
    private static long lastFrame;
    private static final AtomicLong SECTION_REBUILDS = new AtomicLong();
    private static Traffic clientbound = new Traffic();
    private static Traffic serverbound = new Traffic();
    private static long clientTicks;
    /** Changes per block, under their lock: counted on the server thread, summarized on the client's. */
    private static Map<Change, long[]> blockChanges = new HashMap<>();
    /** Changes per position under the same lock, and the changes at positions past the bound. */
    private static Map<Spot, SpotChanges> positionChanges = new HashMap<>();
    private static long untrackedChanges;
    private static final Object BLOCK_CHANGES_LOCK = new Object();
    /** Spawns per particle type on each side, and the spawners of the traced ones, under their lock. */
    private static Map<String, int[]> clientSpawns = new HashMap<>();
    private static Map<String, int[]> serverSpawns = new HashMap<>();
    private static Map<String, Map<Spawner, int[]>> spawners = new HashMap<>();
    private static final Object SPAWNERS_LOCK = new Object();
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
    private static final Deque<Long> ENTITY_RENDER_STARTS = new ArrayDeque<>();
    private static long particleTickStart;
    private static long stageStart;

    private Perf() {
    }

    public static synchronized String name() {
        return name;
    }

    /** Which capture runs or ran last, so per-capture caches elsewhere know when to start over. */
    public static int capture() {
        return capture;
    }

    public static double seconds() {
        return active ? (System.nanoTime() - startNanos) / 1e9 : 0;
    }

    public static synchronized void start(String captureName) {
        start(captureName, null);
    }

    /** Starts a capture; with a server thread, also samples its stack (a profiled capture). */
    public static synchronized void start(String captureName, Thread server) {
        name = captureName;
        serverTicking = false;
        sampler = server == null ? null : new StackSampler(server, () -> serverTicking);
        capture++;
        serverBlockEntities = new Timings();
        serverEntities = new Timings();
        clientBlockEntities = new Timings();
        clientEntities = new Timings();
        renderers = new Timings();
        entityRenderers = new Timings();
        particleTicks = new Timings();
        particleRenders = new Timings();
        renderStages = new Timings();
        tickMillis = Collections.synchronizedList(new ArrayList<>());
        tickLabels = Collections.synchronizedList(new ArrayList<>());
        tickWorks = Collections.synchronizedList(new ArrayList<>());
        tickWork = new int[4];
        endedTickWork = new int[4];
        frameMillis = Collections.synchronizedList(new ArrayList<>());
        lastFrame = 0;
        SECTION_REBUILDS.set(0);
        clientbound = new Traffic();
        serverbound = new Traffic();
        clientTicks = 0;
        synchronized (BLOCK_CHANGES_LOCK) {
            blockChanges = new HashMap<>();
            positionChanges = new HashMap<>();
            untrackedChanges = 0;
        }
        synchronized (SPAWNERS_LOCK) {
            clientSpawns = new HashMap<>();
            serverSpawns = new HashMap<>();
            spawners = new HashMap<>();
        }
        RENDER_STARTS.clear();
        ENTITY_RENDER_STARTS.clear();
        particleTickStart = 0;
        stageStart = 0;
        heapStart = usedHeap();
        heapPeak = heapStart;
        gcCountStart = gcCount();
        gcTimeStart = gcTime();
        startNanos = System.nanoTime();
        active = true;
    }

    // Hooks called by the instrumentation mixins. Each returns at once unless a capture is active.

    /** At the start and the end of each integrated server tick. */
    public static void serverTicking(boolean ticking) {
        if (active) {
            serverTicking = ticking;
        }
    }

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

    /** Ends an earlier step of a render that {@link #renderEnd} completes. */
    public static void renderStepEnd(String type) {
        if (active && !RENDER_STARTS.isEmpty()) {
            renderers.addTime(type, System.nanoTime() - RENDER_STARTS.pop());
        }
    }

    public static void entityRenderStart() {
        if (active) {
            ENTITY_RENDER_STARTS.push(System.nanoTime());
        }
    }

    public static void entityRenderEnd(String type) {
        if (active && !ENTITY_RENDER_STARTS.isEmpty()) {
            entityRenderers.add(type, System.nanoTime() - ENTITY_RENDER_STARTS.pop());
        }
    }

    /** Ends an earlier step of an entity render that {@link #entityRenderEnd} completes. */
    public static void entityRenderStepEnd(String type) {
        if (active && !ENTITY_RENDER_STARTS.isEmpty()) {
            entityRenderers.addTime(type, System.nanoTime() - ENTITY_RENDER_STARTS.pop());
        }
    }

    public static void particleTickStart() {
        if (active) {
            particleTickStart = System.nanoTime();
        }
    }

    public static void particleTickEnd(String type) {
        if (active && particleTickStart != 0) {
            particleTicks.add(type, System.nanoTime() - particleTickStart);
            particleTickStart = 0;
        }
    }

    public static void particleRendered(String type, long nanos) {
        if (active) {
            particleRenders.add(type, nanos);
        }
    }

    /** At the start of a render-level stage, before its other handlers (NeoForge). */
    public static void stageStart() {
        if (active) {
            stageStart = System.nanoTime();
        }
    }

    /** At the end of a render-level stage, after its other handlers. */
    public static void stageEnd(String stage) {
        if (active && stageStart != 0) {
            renderStages.add(stage, System.nanoTime() - stageStart);
            stageStart = 0;
        }
    }

    public static void sectionRebuilt() {
        if (active) {
            SECTION_REBUILDS.incrementAndGet();
        }
    }

    /** A block change the server marks for sending to clients, on the server thread. */
    static void blockChanged(Change change, Spot spot) {
        if (active) {
            synchronized (BLOCK_CHANGES_LOCK) {
                blockChanges.computeIfAbsent(change, key -> new long[1])[0]++;
                SpotChanges at = positionChanges.size() < MAX_TRACKED_POSITIONS
                    ? positionChanges.computeIfAbsent(spot, key -> new SpotChanges()) : positionChanges.get(spot);
                if (at == null) {
                    untrackedChanges++;
                } else {
                    at.count++;
                    at.block = change.block();
                    at.sub = change.sub();
                }
            }
        }
    }

    /** Counts a particle spawned (on the client) or sent (by the server); true when this one is to be traced. */
    public static boolean traceSpawn(String type, boolean server) {
        if (!active) {
            return false;
        }
        synchronized (SPAWNERS_LOCK) {
            return (server ? serverSpawns : clientSpawns).computeIfAbsent(type, key -> new int[1])[0]++
                % SOURCE_SAMPLING == 0;
        }
    }

    /** A traced particle of the type and where it came from. */
    public static void spawnedBy(String type, String mod, String className, String via) {
        if (active) {
            synchronized (SPAWNERS_LOCK) {
                spawners.computeIfAbsent(type, key -> new HashMap<>())
                    .computeIfAbsent(new Spawner(mod, className, via), key -> new int[1])[0]++;
            }
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

    /** A chunk became full in a server level, read from disk or generated; on the server thread. */
    public static void chunkLoaded() {
        if (active) {
            tickWork[WORK_CHUNKS_FULL]++;
        }
    }

    public static void chunkGenerated() {
        if (active) {
            tickWork[WORK_CHUNKS_GENERATED]++;
        }
    }

    public static void chunkSent() {
        if (active) {
            tickWork[WORK_CHUNKS_SENT]++;
        }
    }

    public static void entityAdded() {
        if (active) {
            tickWork[WORK_ENTITIES_ADDED]++;
        }
    }

    /**
     * Once per server tick, with the duration of the last completed tick and the game tick it ended on. That tick is
     * the one before the hook's, so its counts are those this hook held back last time.
     */
    public static void serverTick(long lastTickNanos, long gameTick) {
        if (!active) {
            return;
        }
        if (lastTickNanos > 0) {
            // One lock for the lists, so the timeline's arrays stay the same length.
            synchronized (tickMillis) {
                tickMillis.add(lastTickNanos / 1e6);
                tickLabels.add(gameTick);
                tickWorks.add(endedTickWork);
            }
        }
        endedTickWork = tickWork;
        tickWork = new int[4];
    }

    /**
     * Ends the capture and builds its summary. The counts of loaded block entities and entities and of live
     * particles are filled in by the caller, which can reach the game.
     */
    public static synchronized JsonObject stop(JsonObject serverCounts, Map<String, Integer> liveParticles) {
        active = false;
        JsonObject stacks = sampler == null ? null : sampler.stop();
        sampler = null;
        double seconds = (System.nanoTime() - startNanos) / 1e9;
        JsonObject summary = new JsonObject();
        summary.addProperty("name", name);
        summary.addProperty("duration_s", round(seconds));

        double[] ticks;
        long[] labels;
        int[][] works;
        synchronized (tickMillis) {
            ticks = toArray(tickMillis);
            labels = tickLabels.stream().mapToLong(Long::longValue).toArray();
            works = tickWorks.toArray(new int[0][]);
        }
        JsonObject server = new JsonObject();
        if (ticks.length > 0) {
            JsonObject mspt = new JsonObject();
            mspt.addProperty("avg", round(Stats.average(ticks)));
            mspt.addProperty("p95", round(Stats.percentile(ticks, 0.95)));
            mspt.addProperty("p99", round(Stats.percentile(ticks, 0.99)));
            mspt.addProperty("max", round(Stats.max(ticks)));
            server.add("mspt", mspt);
            server.addProperty("tps", round(ticks.length / seconds));
            server.addProperty("ticks", ticks.length);
            server.add("spikes", spikes(ticks, labels, works));
            int[] total = new int[4];
            for (int[] work : works) {
                for (int kind = 0; kind < total.length; kind++) {
                    total[kind] += work[kind];
                }
            }
            JsonObject chunks = new JsonObject();
            chunks.addProperty("generated", total[WORK_CHUNKS_GENERATED]);
            chunks.addProperty("loaded", total[WORK_CHUNKS_FULL] - total[WORK_CHUNKS_GENERATED]);
            chunks.addProperty("sent", total[WORK_CHUNKS_SENT]);
            server.add("chunks", chunks);
            server.addProperty("entities_added", total[WORK_ENTITIES_ADDED]);
            synchronized (BLOCK_CHANGES_LOCK) {
                server.add("block_changes", ranked(blockChanges, seconds));
                server.add("block_change_positions", rankedPositions(positionChanges, seconds));
                if (untrackedChanges > 0) {
                    server.addProperty("block_change_positions_untracked", untrackedChanges);
                }
            }
            server.add("timeline", timeline(ticks, labels, works));
            server.add("block_entities", serverBlockEntities.ranked(ticks.length, "ms_per_tick"));
            server.add("entities", serverEntities.ranked(ticks.length, "ms_per_tick"));
            if (serverCounts != null) {
                server.add("loaded", serverCounts);
            }
        }
        if (stacks != null) {
            server.add("stacks", stacks);
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
        client.add("entity_renderers", entityRenderers.ranked(frames.length, "ms_per_frame"));
        JsonObject particles = new JsonObject();
        particles.add("renders", particleRenders.ranked(frames.length, "ms_per_frame"));
        particles.add("ticks", particleTicks.ranked(clientTicks, "ms_per_tick"));
        JsonObject live = new JsonObject();
        liveParticles.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue())
            .forEach(entry -> live.addProperty(entry.getKey(), entry.getValue()));
        particles.add("live", live);
        synchronized (SPAWNERS_LOCK) {
            particles.add("sources", sources(spawners));
        }
        client.add("particles", particles);
        client.add("render_stages", renderStages.ranked(frames.length, "ms_per_frame"));
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
            + clientBlockEntities.calls() + clientEntities.calls() + renderers.calls() + entityRenderers.calls()
            + particleTicks.calls() + particleRenders.calls() + renderStages.calls());
        return summary;
    }

    /**
     * The ticks over {@link #SPIKE_MS}, longest first, at most {@link #MAX_SPIKES}, as {@code {tick, ms}} and what the
     * server did in them, leaving out counts of 0.
     */
    static JsonArray spikes(double[] ticks, long[] labels, int[][] works) {
        JsonArray spikes = new JsonArray();
        IntStream.range(0, ticks.length).filter(index -> ticks[index] > SPIKE_MS).boxed()
            .sorted((a, b) -> Double.compare(ticks[b], ticks[a])).limit(MAX_SPIKES).forEach(index -> {
                JsonObject spike = new JsonObject();
                spike.addProperty("tick", labels[index]);
                spike.addProperty("ms", round(ticks[index]));
                int[] work = works[index];
                addCount(spike, "chunks_generated", work[WORK_CHUNKS_GENERATED]);
                addCount(spike, "chunks_loaded", work[WORK_CHUNKS_FULL] - work[WORK_CHUNKS_GENERATED]);
                addCount(spike, "chunks_sent", work[WORK_CHUNKS_SENT]);
                addCount(spike, "entities_added", work[WORK_ENTITIES_ADDED]);
                spikes.add(spike);
            });
        return spikes;
    }

    private static void addCount(JsonObject object, String name, int count) {
        if (count > 0) {
            object.addProperty(name, count);
        }
    }

    /** Every tick's game tick, time and what the server did in it, as arrays of the same length. */
    private static JsonObject timeline(double[] ticks, long[] labels, int[][] works) {
        String[] names = {"tick", "ms", "chunks_generated", "chunks_loaded", "chunks_sent", "entities_added"};
        JsonArray[] arrays = new JsonArray[names.length];
        for (int column = 0; column < names.length; column++) {
            arrays[column] = new JsonArray();
        }
        for (int index = 0; index < ticks.length; index++) {
            int[] work = works[index];
            arrays[0].add(labels[index]);
            arrays[1].add(round(ticks[index]));
            arrays[2].add(work[WORK_CHUNKS_GENERATED]);
            arrays[3].add(work[WORK_CHUNKS_FULL] - work[WORK_CHUNKS_GENERATED]);
            arrays[4].add(work[WORK_CHUNKS_SENT]);
            arrays[5].add(work[WORK_ENTITIES_ADDED]);
        }
        JsonObject timeline = new JsonObject();
        for (int column = 0; column < names.length; column++) {
            timeline.add(names[column], arrays[column]);
        }
        return timeline;
    }

    /**
     * Per particle type traced, most traced first, its top sources by share of its traced spawns, as
     * {@code {type, mod?, class?, via, share}}.
     */
    static JsonArray sources(Map<String, Map<Spawner, int[]>> traced) {
        JsonArray array = new JsonArray();
        traced.entrySet().stream()
            .sorted((a, b) -> Integer.compare(total(b.getValue()), total(a.getValue())))
            .forEach(type -> {
                int total = total(type.getValue());
                type.getValue().entrySet().stream().sorted((a, b) -> Integer.compare(b.getValue()[0], a.getValue()[0]))
                    .limit(SOURCES_PER_TYPE).forEach(source -> {
                        JsonObject object = new JsonObject();
                        object.addProperty("type", type.getKey());
                        if (source.getKey().mod() != null) {
                            object.addProperty("mod", source.getKey().mod());
                        }
                        if (source.getKey().className() != null) {
                            object.addProperty("class", source.getKey().className());
                        }
                        object.addProperty("via", source.getKey().via());
                        object.addProperty("share", round((double) source.getValue()[0] / total));
                        array.add(object);
                    });
            });
        return array;
    }

    private static int total(Map<Spawner, int[]> sources) {
        return sources.values().stream().mapToInt(count -> count[0]).sum();
    }

    /** The most frequent block changes, as {@code {block, be?, sub?, count, per_s}}, most first. */
    static JsonArray ranked(Map<Change, long[]> changes, double seconds) {
        JsonArray array = new JsonArray();
        changes.entrySet().stream().sorted((a, b) -> Long.compare(b.getValue()[0], a.getValue()[0]))
            .limit(MAX_BLOCK_CHANGES).forEach(entry -> {
                JsonObject object = new JsonObject();
                object.addProperty("block", entry.getKey().block());
                if (entry.getKey().blockEntity() != null) {
                    object.addProperty("be", entry.getKey().blockEntity());
                }
                if (entry.getKey().sub() != null) {
                    object.addProperty("sub", entry.getKey().sub());
                }
                object.addProperty("count", entry.getValue()[0]);
                object.addProperty("per_s", round(seconds <= 0 ? 0 : entry.getValue()[0] / seconds));
                array.add(object);
            });
        return array;
    }

    /** The busiest positions, as {@code {pos, block, sub?, dim?, count, per_s}}, most first. */
    static JsonArray rankedPositions(Map<Spot, SpotChanges> changes, double seconds) {
        JsonArray array = new JsonArray();
        changes.entrySet().stream().sorted((a, b) -> Long.compare(b.getValue().count, a.getValue().count))
            .limit(MAX_BLOCK_CHANGE_POSITIONS).forEach(entry -> {
                Spot spot = entry.getKey();
                SpotChanges at = entry.getValue();
                JsonObject object = new JsonObject();
                JsonArray pos = new JsonArray();
                pos.add(spot.x());
                pos.add(spot.y());
                pos.add(spot.z());
                object.add("pos", pos);
                object.addProperty("block", at.block);
                if (at.sub != null) {
                    object.addProperty("sub", at.sub);
                }
                if (!spot.dimension().equals(OVERWORLD)) {
                    object.addProperty("dim", spot.dimension());
                }
                object.addProperty("count", at.count);
                object.addProperty("per_s", round(seconds <= 0 ? 0 : at.count / seconds));
                array.add(object);
            });
        return array;
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

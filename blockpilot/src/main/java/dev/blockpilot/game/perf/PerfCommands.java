package dev.blockpilot.game.perf;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.blockpilot.api.Action;
import dev.blockpilot.api.ArgType;
import dev.blockpilot.api.Args;
import dev.blockpilot.api.CommandContext;
import dev.blockpilot.api.CommandException;
import dev.blockpilot.api.CommandSpec;
import dev.blockpilot.api.Duration;
import dev.blockpilot.api.Fields;
import dev.blockpilot.api.Numbers;
import dev.blockpilot.api.Position;
import dev.blockpilot.api.Result;
import dev.blockpilot.api.Row;
import dev.blockpilot.game.common.Ids;
import dev.blockpilot.game.common.TimedStop;
import dev.blockpilot.game.sable.SableSupport;
import dev.blockpilot.game.sable.SubLevels;
import dev.blockpilot.mixin.ChunkMapAccessor;
import dev.blockpilot.mixin.ParticleEngineAccessor;
//? if >=26.1 {
/*import dev.blockpilot.mixin.ParticleGroupAccessor;
*///?}
import dev.blockpilot.platform.ClientPlatform;
import dev.blockpilot.protocol.CommandRegistry;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
//? if >=26.1 {
/*import net.minecraft.client.particle.ParticleGroup;
*///?}
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.profiling.ProfileResults;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;

/** {@code perf start|stop|status}: performance captures summarized as JSON and saved under blockpilot/perf/. */
public final class PerfCommands {
    private static final int TOP = 5;
    private static final Gson GSON = new GsonBuilder().create();
    private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().create();
    private static final int COUNT_WAIT_TICKS = 100;
    /** The share of the whole profile a section needs for its own row in the text reply, in percent. */
    private static final double SECTION_ROW_MIN = 5;

    public void register(CommandRegistry registry) {
        registry.register(CommandSpec.action("perf", this::perf).group("performance")
            // profile right after start is the keyword, not a capture name.
            .form(form -> form.keyword("op", "start").keyword("profile", "profile").optionalArg("name", ArgType.WORD))
            .form(form -> form.keyword("op", "start")
                .optional(named -> named.arg("name", ArgType.WORD).optionalKeyword("profile", "profile")))
            .form(form -> form.keyword("op", "stop", "status"))
            .form(form -> form.arg("for", ArgType.DURATION).keyword("profile", "profile")
                .optionalArg("name", ArgType.WORD))
            .form(form -> form.arg("for", ArgType.DURATION)
                .optional(named -> named.arg("name", ArgType.WORD).optionalKeyword("profile", "profile")))
            .help("perf start [name] … perf stop: server MSPT (avg/p95/p99/max), TPS, the ticks over 50 ms by game "
                + "tick with the chunks generated, loaded and sent and the entities added in them (every tick's are in "
                + "the file), tick time per block-entity and entity type, the chunks loaded in the world and in "
                + "sub-level plots and, with Sable, the sub-levels loaded at the end, the block "
                + "changes the server sends to clients per block, block entity type and sub-level, and the 50 "
                + "positions with the most (a capture tracks the first 65,536 positions it sees; "
                + "block_change_positions_untracked counts the changes at the others), "
                + "client FPS/1% low/frame times, block-entity and entity renderer time per type, particle tick and "
                + "render time per type with live counts and the mod and class that spawned them on the client or "
                + "sent them on the integrated server, "
                + "render-level stage time (NeoForge), section rebuilds, "
                + "heap/GC, packets per type; summary returned and saved to blockpilot/perf/<name>.json. With profile "
                + "(integrated server), stack samples of the server thread while it ticks, about 20 a second, each "
                + "counted toward the mod owning the innermost frame outside the JDK, Minecraft, the loaders and Mixin "
                + "and toward its top frame (native ones marked): stacks in the summary, mod and frame rows; and the "
                + "server's metrics recorder (as F3+L, which must not be running), which records the first 10 s: "
                + "sections in the summary with their share of the parent and of the whole, down to 0.5%, unspecified "
                + "kept, the seconds covered and the recorder's report, kept as <name>-profile beside the summary, a "
                + "sections row and section rows for those at 5% or more. perf <duration> [name] [profile] starts a "
                + "capture, ends it after that long on the wall clock (a tick counting 50 ms) and answers as perf stop "
                + "does; stop cancels the wait and leaves the capture running until perf stop")
            .field("summary", "the full summary (JSON mode)").field("file", "where it was saved")
            .example("perf start before"));
    }

    private Action perf(CommandContext context, Args args) throws CommandException {
        if (args.has("for")) {
            start(args);
            return new Timed(args.duration("for"));
        }
        switch (args.word("op")) {
            case "start" -> {
                String name = start(args);
                return () -> Result.ok(new Fields().put("capture", name, "capturing " + name));
            }
            case "status" -> {
                return () -> Result.ok(Perf.active
                    ? new Fields().bare("capture", Perf.name()).put("seconds", Perf.seconds(),
                        Numbers.format(Perf.seconds()) + "s")
                    : new Fields().put("capture", "none", "no capture running"));
            }
            default -> {
                if (!Perf.active) {
                    throw new CommandException("no_capture");
                }
                return new Stop();
            }
        }
    }

    /** Starts a capture by the name given or one made from the time, profiled with {@code profile}; gives the name. */
    private static String start(Args args) throws CommandException {
        if (Perf.active) {
            throw new CommandException("busy", "capture " + Perf.name() + " is running; perf stop first");
        }
        String name = args.has("name") ? args.word("name")
            : "capture-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        if (!name.matches("[A-Za-z0-9_.-]{1,64}") || name.startsWith(".")) {
            throw new CommandException("syntax", "capture names use letters, digits, _ . - (max 64)");
        }
        Perf.start(name, args.has("profile") ? Profiling.start() : null);
        return name;
    }

    /**
     * {@code perf <duration>}: waits that long on the wall clock, then stops the capture as {@code perf stop} does.
     */
    private static final class Timed extends TimedStop {
        Timed(Duration duration) {
            super(duration);
        }

        @Override
        protected Action stop() {
            return new Stop();
        }

        @Override
        public String label() {
            return "perf";
        }
    }

    /** What a profiled capture needs of the integrated server, kept apart so tests load no Minecraft classes. */
    private static final class Profiling {
        /** The recorder's results and the path of the report it saves, for the running profiled capture. */
        private static CompletableFuture<ProfileResults> results;
        private static CompletableFuture<Path> report;

        /** Starts the server's metrics recorder and gives the thread to sample. */
        static Thread start() throws CommandException {
            MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
            if (server == null) {
                throw new CommandException("no_server", "profile samples the integrated server");
            }
            if (server.isRecordingMetrics()) {
                throw new CommandException("busy", "the server's metrics recorder runs (F3+L, or /perf on a "
                    + "dedicated server); let it end first");
            }
            CompletableFuture<ProfileResults> recorded = new CompletableFuture<>();
            CompletableFuture<Path> saved = new CompletableFuture<>();
            results = recorded;
            report = saved;
            server.execute(() -> server.startRecordingMetrics(recorded::complete, saved::complete));
            return server.getRunningThread();
        }

        /** Ends the recorder if it still runs, which hands over its results at the end of the tick; server thread. */
        static void finish(MinecraftServer server) {
            if (server.isRecordingMetrics()) {
                server.finishRecordingMetrics();
            }
        }

        /** Whether the results and the report are in. */
        static boolean done() {
            return results.isDone() && report.isDone();
        }

        /**
         * Adds the sections, the seconds they cover and the report, as far as they are in, to the server's part. The
         * recorder writes its report to a temporary folder; it moves next to the summary as {@code <name>-profile}.
         */
        static void addTo(JsonObject server, String name) {
            ProfileResults recorded = results.getNow(null);
            if (recorded != null) {
                // Each list starts with the section itself, then its children.
                server.add("sections", SectionTree.of(path -> recorded.getTimes(path).stream().skip(1)
                    .map(field -> new SectionTree.Section(field.name, field.percentage, field.globalPercentage,
                        field.count)).toList(), ProfileResults.PATH_SEPARATOR, recorded.getTickDuration()));
                server.addProperty("sections_s", Perf.round(recorded.getNanoDuration() / 1e9));
            }
            Path saved = report.getNow(null);
            if (saved != null) {
                server.addProperty("profile_report", keep(saved, name).toAbsolutePath().toString());
            }
        }
    }

    /** The loaded counts at the end of a capture, kept apart so tests load no Minecraft classes. */
    private static final class Counts {
        /**
         * Loaded block entities and entities per type, chunks in the world and in sub-level plots, and with Sable the
         * sub-levels, across the server's levels, after ending the metrics recorder of a profiled capture; on the
         * server thread.
         */
        static JsonObject of(MinecraftServer server, boolean profiled) {
            if (profiled) {
                Profiling.finish(server);
            }
            Map<String, Integer> blockEntities = new TreeMap<>();
            Map<String, Integer> entities = new TreeMap<>();
            SubLevels subLevels = SableSupport.subLevels();
            int worldChunks = 0;
            int plotChunks = 0;
            int subLevelCount = 0;
            for (ServerLevel level : server.getAllLevels()) {
                for (ChunkHolder holder : ((ChunkMapAccessor) level.getChunkSource().chunkMap)
                    .blockpilot$visibleChunks().values()) {
                    ChunkPos pos = holder.getPos();
                    if (subLevels.at(level, new BlockPos(pos.getMinBlockX(), 0, pos.getMinBlockZ())) != null) {
                        // A plot's chunk, counted below.
                        continue;
                    }
                    //? if >=1.21 {
                    if (holder.getLatestChunk() instanceof LevelChunk) {
                    //?} else {
                    /*if (holder.getLastAvailable() instanceof LevelChunk) {
                    *///?}
                        worldChunks++;
                    }
                    countBlockEntities(holder.getTickingChunk(), blockEntities);
                }
                // Sable keeps plot chunks out of the chunk map's full chunks and hands them out by position.
                Set<ChunkPos> plots = new HashSet<>();
                for (SubLevels.Info subLevel : subLevels.all(level)) {
                    subLevelCount++;
                    AABB bounds = subLevel.plotBounds();
                    for (int x = SectionPos.blockToSectionCoord(bounds.minX);
                        x <= SectionPos.blockToSectionCoord(bounds.maxX); x++) {
                        for (int z = SectionPos.blockToSectionCoord(bounds.minZ);
                            z <= SectionPos.blockToSectionCoord(bounds.maxZ); z++) {
                            LevelChunk chunk = level.getChunkSource().getChunkNow(x, z);
                            if (chunk != null && plots.add(new ChunkPos(x, z))) {
                                countBlockEntities(chunk, blockEntities);
                            }
                        }
                    }
                }
                plotChunks += plots.size();
                for (Entity entity : level.getAllEntities()) {
                    entities.merge(Ids.of(entity), 1, Integer::sum);
                }
            }
            JsonObject result = new JsonObject();
            result.add("block_entities", GSON.toJsonTree(blockEntities));
            result.add("entities", GSON.toJsonTree(entities));
            JsonObject chunks = new JsonObject();
            chunks.addProperty("world", worldChunks);
            chunks.addProperty("plots", plotChunks);
            result.add("chunks", chunks);
            if (subLevels != SubLevels.NONE) {
                result.addProperty("sub_levels", subLevelCount);
            }
            return result;
        }

        private static void countBlockEntities(LevelChunk chunk, Map<String, Integer> blockEntities) {
            if (chunk != null) {
                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    blockEntities.merge(Ids.of(blockEntity.getType()), 1, Integer::sum);
                }
            }
        }
    }

    /** Stops the capture: asks the integrated server for its counts (without blocking), then builds the summary. */
    private static final class Stop implements Action {
        private CompletableFuture<JsonObject> counts;
        private boolean profiled;
        private JsonObject summary;
        private int waited;

        @Override
        public Result tick() throws CommandException {
            if (counts == null) {
                MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
                profiled = Perf.profiled();
                counts = server == null ? CompletableFuture.completedFuture(null)
                    : server.submit(() -> Counts.of(server, profiled));
            }
            if (!counts.isDone() && waited++ < COUNT_WAIT_TICKS) {
                return null;
            }
            if (summary == null) {
                summary = Perf.stop(counts.getNow(null), liveParticles());
            }
            // The capture has ended; the recorder's results and report may take a few more ticks.
            if (profiled && !Profiling.done() && waited++ < COUNT_WAIT_TICKS) {
                return null;
            }
            if (profiled) {
                Profiling.addTo(summary.getAsJsonObject("server"), summary.get("name").getAsString());
            }
            Path file = save(summary);
            JsonObject reply = forReply(summary);
            return Result.ok(overview(reply, file)).withRows(top(reply));
        }

        @Override
        public String label() {
            return "perf";
        }
    }

    /** The particles alive now, per type; on the client thread. */
    private static Map<String, Integer> liveParticles() {
        Map<String, Integer> live = new HashMap<>();
        //? if >=26.1 {
        /*for (ParticleGroup<?> group : ((ParticleEngineAccessor) Minecraft.getInstance().particleEngine)
            .blockpilot$particles().values()) {
            for (Particle particle : ((ParticleGroupAccessor) group).blockpilot$particles()) {
        *///?} else {
        for (Queue<Particle> queue : ((ParticleEngineAccessor) Minecraft.getInstance().particleEngine)
            .blockpilot$particles().values()) {
            for (Particle particle : queue) {
        //?}
                live.merge(ParticleTag.of(particle), 1, Integer::sum);
            }
        }
        return live;
    }

    private static Path directory() {
        return ClientPlatform.gameDir().resolve("blockpilot").resolve("perf");
    }

    /** Moves a recorder report from its temporary folder to {@code <name>-profile} beside the summary, if it can. */
    private static Path keep(Path report, String name) {
        Path kept = directory().resolve(name + "-profile");
        try (Stream<Path> files = Files.walk(report)) {
            Files.createDirectories(kept);
            for (Path file : files.toList()) {
                Path target = kept.resolve(report.relativize(file).toString());
                if (Files.isDirectory(file)) {
                    Files.createDirectories(target);
                } else {
                    Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (IOException e) {
            return report;
        }
        try (Stream<Path> files = Files.walk(report)) {
            for (Path file : files.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(file);
            }
        } catch (IOException e) {
            // The copy is kept; a leftover temporary folder does no harm.
        }
        return kept;
    }

    private static Path save(JsonObject summary) throws CommandException {
        Path directory = directory();
        Path file = directory.resolve(summary.get("name").getAsString() + ".json");
        try {
            Files.createDirectories(directory);
            Files.writeString(file, PRETTY.toJson(summary), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new CommandException("internal", "could not save the summary: " + e.getMessage());
        }
        return file.toAbsolutePath();
    }

    /** The summary without the per-tick timeline, which only the file keeps. */
    static JsonObject forReply(JsonObject summary) {
        JsonObject reply = summary.deepCopy();
        reply.getAsJsonObject("server").remove("timeline");
        return reply;
    }

    /** One line: duration, MSPT, TPS, FPS and 1% low, the file; the full summary as a JSON-only field. */
    private static Fields overview(JsonObject summary, Path file) {
        JsonObject server = summary.getAsJsonObject("server");
        JsonObject client = summary.getAsJsonObject("client");
        StringBuilder text = new StringBuilder(summary.get("name").getAsString()).append(' ')
            .append(Numbers.format(summary.get("duration_s").getAsDouble())).append('s');
        if (server.has("mspt")) {
            JsonObject mspt = server.getAsJsonObject("mspt");
            text.append(" mspt=").append(number(mspt, "avg")).append('/').append(number(mspt, "p95")).append('/')
                .append(number(mspt, "p99")).append('/').append(number(mspt, "max")).append(" tps=")
                .append(number(server, "tps"));
        }
        JsonObject fps = client.getAsJsonObject("fps");
        text.append(" fps=").append(number(fps, "avg")).append(" low1%=").append(number(fps, "low_1pct"))
            .append(" rebuilds/s=").append(number(client.getAsJsonObject("sections"), "per_s"));
        return new Fields().put("summary", summary, text.toString()).bare("file", file.toString());
    }

    private static String number(JsonObject object, String key) {
        return Numbers.format(object.get(key).getAsDouble());
    }

    /** The top entries of each list, for the text overview. */
    private static List<Row> top(JsonObject summary) {
        List<Row> rows = new ArrayList<>();
        JsonObject server = summary.getAsJsonObject("server");
        JsonObject client = summary.getAsJsonObject("client");
        JsonObject network = summary.getAsJsonObject("network");
        if (server.has("spikes")) {
            server.getAsJsonArray("spikes").asList().stream().limit(TOP).map(JsonElement::getAsJsonObject)
                .forEach(spike -> {
                    Fields fields = new Fields().bare("list", "spike")
                        .put("tick", spike.get("tick").getAsLong(), "t" + spike.get("tick"))
                        .put("ms", spike.get("ms").getAsDouble(), Numbers.format(spike.get("ms").getAsDouble()) + "ms");
                    for (String count : List.of("chunks_generated", "chunks_loaded", "chunks_sent", "entities_added")) {
                        if (spike.has(count)) {
                            fields.put(count, spike.get(count).getAsInt(), count + "=" + spike.get(count));
                        }
                    }
                    rows.add(Row.of("spike:t" + spike.get("tick").getAsLong(), fields));
                });
        }
        if (server.has("stacks")) {
            stackRows(rows, server.getAsJsonObject("stacks"));
        }
        if (server.has("sections")) {
            double seconds = server.get("sections_s").getAsDouble();
            Fields covered = new Fields().bare("list", "sections").put("seconds", seconds,
                Numbers.format(seconds) + "s");
            if (server.has("profile_report")) {
                covered.bare("report", server.get("profile_report").getAsString());
            }
            rows.add(Row.of("sections", covered));
            for (JsonObject section : SectionTree.atLeast(server.getAsJsonArray("sections"), SECTION_ROW_MIN)) {
                String path = section.get("path").getAsString();
                rows.add(Row.of("section:" + path, share(new Fields().bare("list", "section").bare("type", path),
                    section.get("of_whole").getAsDouble())));
            }
        }
        if (server.has("loaded") && server.getAsJsonObject("loaded").has("chunks")) {
            JsonObject loaded = server.getAsJsonObject("loaded");
            JsonObject chunks = loaded.getAsJsonObject("chunks");
            Fields fields = new Fields().bare("list", "loaded")
                .put("world", chunks.get("world").getAsInt(), "chunks world=" + chunks.get("world").getAsInt())
                .put("plots", chunks.get("plots").getAsInt());
            if (loaded.has("sub_levels")) {
                fields.put("sub_levels", loaded.get("sub_levels").getAsInt());
            }
            rows.add(Row.of("loaded", fields));
        }
        if (server.has("block_changes")) {
            server.getAsJsonArray("block_changes").asList().stream().limit(TOP).map(JsonElement::getAsJsonObject)
                .forEach(change -> {
                    String where = changedBlock(change);
                    double rate = change.get("per_s").getAsDouble();
                    rows.add(Row.of("change:" + where, new Fields().bare("list", "change").bare("type", where)
                        .put("per_s", rate, Numbers.format(rate) + "/s")));
                });
        }
        if (server.has("block_change_positions")) {
            server.getAsJsonArray("block_change_positions").asList().stream().limit(TOP)
                .map(JsonElement::getAsJsonObject).forEach(change -> {
                    JsonArray pos = change.getAsJsonArray("pos");
                    Position at = Position.absolute(pos.get(0).getAsInt(), pos.get(1).getAsInt(),
                        pos.get(2).getAsInt());
                    double rate = change.get("per_s").getAsDouble();
                    rows.add(Row.of("change at:" + at, new Fields().bare("list", "change at").bare("at", at)
                        .bare("type", changedBlock(change)).put("per_s", rate, Numbers.format(rate) + "/s")));
                });
        }
        addTop(rows, "server be", server.getAsJsonArray("block_entities"), "ms_per_tick", "ms/t");
        addTop(rows, "server entity", server.getAsJsonArray("entities"), "ms_per_tick", "ms/t");
        addTop(rows, "renderer", client.getAsJsonArray("block_entity_renderers"), "ms_per_frame", "ms/frame");
        addTop(rows, "entity renderer", client.getAsJsonArray("entity_renderers"), "ms_per_frame", "ms/frame");
        JsonObject particles = client.getAsJsonObject("particles");
        addTop(rows, "particle render", particles.getAsJsonArray("renders"), "ms_per_frame", "ms/frame");
        addTop(rows, "particle tick", particles.getAsJsonArray("ticks"), "ms_per_tick", "ms/t");
        if (particles.has("sources")) {
            particles.getAsJsonArray("sources").asList().stream().limit(TOP).map(JsonElement::getAsJsonObject)
                .forEach(source -> {
                    String type = source.get("type").getAsString();
                    String from = from(source);
                    double share = source.get("share").getAsDouble();
                    rows.add(Row.of("source:" + type + ":" + from, new Fields().bare("list", "source")
                        .bare("type", type).bare("from", from)
                        .put("share", share, Math.round(share * 100) + "%")));
                });
        }
        addTop(rows, "stage", client.getAsJsonArray("render_stages"), "ms_per_frame", "ms/frame");
        addTop(rows, "client be", client.getAsJsonArray("block_entity_ticks"), "ms_per_tick", "ms/t");
        addTop(rows, "in", network.getAsJsonArray("clientbound"), "per_s", "/s");
        addTop(rows, "out", network.getAsJsonArray("serverbound"), "per_s", "/s");
        return rows;
    }

    /** The mods and top frames with the most stack samples, as shares of the samples taken in ticks. */
    private static void stackRows(List<Row> rows, JsonObject stacks) {
        stacks.getAsJsonArray("mods").asList().stream().limit(TOP).map(JsonElement::getAsJsonObject).forEach(mod -> {
            String id = mod.get("mod").getAsString();
            rows.add(Row.of("mod:" + id, share(new Fields().bare("list", "mod").bare("type", id),
                mod.get("share").getAsDouble())));
        });
        stacks.getAsJsonArray("frames").asList().stream().limit(TOP).map(JsonElement::getAsJsonObject)
            .forEach(frame -> {
                String name = frame.get("frame").getAsString();
                Fields fields = new Fields().bare("list", "frame").bare("type", name);
                if (frame.has("native")) {
                    fields.put("native", true, "native");
                }
                if (frame.has("mod")) {
                    fields.bare("mod", frame.get("mod").getAsString());
                }
                rows.add(Row.of("frame:" + name, share(fields, frame.get("share").getAsDouble())));
            });
    }

    private static Fields share(Fields fields, double share) {
        return fields.put("share", share, Numbers.format(share) + "%");
    }

    /** A block change's block, with the sub-level it is aboard and its dimension outside the overworld. */
    private static String changedBlock(JsonObject change) {
        return change.get("block").getAsString() + (change.has("sub") ? " on " + change.get("sub").getAsString() : "")
            + (change.has("dim") ? " in " + change.get("dim").getAsString() : "");
    }

    /** Who spawned a particle, for the text overview: the mod and class, or the server that sent it. */
    private static String from(JsonObject source) {
        String via = source.get("via").getAsString();
        if (via.equals("server")) {
            return "server";
        }
        String mod = source.has("mod") ? source.get("mod").getAsString() : "?";
        String type = source.has("class") ? " " + source.get("class").getAsString()
            .substring(source.get("class").getAsString().lastIndexOf('.') + 1) : "";
        return (via.equals("server_side") ? "sent by " : "") + mod + type;
    }

    private static void addTop(List<Row> rows, String label, JsonArray entries, String key, String unit) {
        if (entries == null) {
            return;
        }
        int shown = 0;
        for (JsonElement element : entries) {
            if (shown++ >= TOP) {
                break;
            }
            JsonObject entry = element.getAsJsonObject();
            String type = entry.get("type").getAsString();
            rows.add(Row.of(label + ":" + type, new Fields().bare("list", label).bare("type", type)
                .put(key, entry.get(key).getAsDouble(), Numbers.format(entry.get(key).getAsDouble()) + unit)));
        }
    }
}

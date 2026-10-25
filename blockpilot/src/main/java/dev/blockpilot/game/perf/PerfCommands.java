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
import dev.blockpilot.api.Fields;
import dev.blockpilot.api.Numbers;
import dev.blockpilot.api.Result;
import dev.blockpilot.api.Row;
import dev.blockpilot.game.Ids;
import dev.blockpilot.mixin.ChunkMapAccessor;
import dev.blockpilot.mixin.ParticleEngineAccessor;
import dev.blockpilot.platform.ClientPlatform;
import dev.blockpilot.protocol.CommandRegistry;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

/** {@code perf start|stop|status}: performance captures summarized as JSON and saved under blockpilot/perf/. */
public final class PerfCommands {
    private static final int TOP = 5;
    private static final Gson GSON = new GsonBuilder().create();
    private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().create();
    private static final int COUNT_WAIT_TICKS = 100;

    public void register(CommandRegistry registry) {
        registry.register(CommandSpec.action("perf", this::perf).group("performance")
            .form(form -> form.keyword("op", "start").optionalArg("name", ArgType.WORD))
            .form(form -> form.keyword("op", "stop", "status"))
            .help("perf start [name] … perf stop: server MSPT/TPS and tick time per block-entity and entity type, "
                + "client FPS/1% low/frame times, block-entity and entity renderer time per type, particle tick and "
                + "render time per type with live counts, render-level stage time (NeoForge), section rebuilds, "
                + "heap/GC, packets per type; summary returned and saved to blockpilot/perf/<name>.json")
            .field("summary", "the full summary (JSON mode)").field("file", "where it was saved")
            .example("perf start before"));
    }

    private Action perf(CommandContext context, Args args) throws CommandException {
        switch (args.word("op")) {
            case "start" -> {
                if (Perf.active) {
                    throw new CommandException("busy", "capture " + Perf.name() + " is running; perf stop first");
                }
                String name = args.has("name") ? args.word("name")
                    : "capture-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
                if (!name.matches("[A-Za-z0-9_.-]{1,64}") || name.startsWith(".")) {
                    throw new CommandException("syntax", "capture names use letters, digits, _ . - (max 64)");
                }
                Perf.start(name);
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

    /** Stops the capture: asks the integrated server for its counts (without blocking), then builds the summary. */
    private static final class Stop implements Action {
        private CompletableFuture<JsonObject> counts;
        private int waited;

        @Override
        public Result tick() throws CommandException {
            if (counts == null) {
                MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
                counts = server == null ? CompletableFuture.completedFuture(null) : server.submit(() -> loaded(server));
            }
            if (!counts.isDone() && waited++ < COUNT_WAIT_TICKS) {
                return null;
            }
            JsonObject summary = Perf.stop(counts.getNow(null), liveParticles());
            Path file = save(summary);
            return Result.ok(overview(summary, file)).withRows(top(summary));
        }

        @Override
        public String label() {
            return "perf";
        }
    }

    /** The particles alive now, per type; on the client thread. */
    private static Map<String, Integer> liveParticles() {
        Map<String, Integer> live = new HashMap<>();
        for (Queue<Particle> queue : ((ParticleEngineAccessor) Minecraft.getInstance().particleEngine)
            .blockpilot$particles().values()) {
            for (Particle particle : queue) {
                live.merge(ParticleTag.of(particle), 1, Integer::sum);
            }
        }
        return live;
    }

    /** Loaded block entities and entities per type across the server's levels; on the server thread. */
    private static JsonObject loaded(MinecraftServer server) {
        Map<String, Integer> blockEntities = new TreeMap<>();
        Map<String, Integer> entities = new TreeMap<>();
        for (ServerLevel level : server.getAllLevels()) {
            for (ChunkHolder holder : ((ChunkMapAccessor) level.getChunkSource().chunkMap).blockpilot$chunks()) {
                LevelChunk chunk = holder.getTickingChunk();
                if (chunk != null) {
                    for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                        blockEntities.merge(Ids.of(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity.getType())), 1,
                            Integer::sum);
                    }
                }
            }
            for (Entity entity : level.getAllEntities()) {
                entities.merge(Ids.of(entity), 1, Integer::sum);
            }
        }
        JsonObject result = new JsonObject();
        result.add("block_entities", GSON.toJsonTree(blockEntities));
        result.add("entities", GSON.toJsonTree(entities));
        return result;
    }

    private static Path save(JsonObject summary) throws CommandException {
        Path directory = ClientPlatform.gameDir().resolve("blockpilot").resolve("perf");
        Path file = directory.resolve(summary.get("name").getAsString() + ".json");
        try {
            Files.createDirectories(directory);
            Files.writeString(file, PRETTY.toJson(summary), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new CommandException("internal", "could not save the summary: " + e.getMessage());
        }
        return file.toAbsolutePath();
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
                .append(number(mspt, "max")).append(" tps=").append(number(server, "tps"));
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
        addTop(rows, "server be", server.getAsJsonArray("block_entities"), "ms_per_tick", "ms/t");
        addTop(rows, "server entity", server.getAsJsonArray("entities"), "ms_per_tick", "ms/t");
        addTop(rows, "renderer", client.getAsJsonArray("block_entity_renderers"), "ms_per_frame", "ms/frame");
        addTop(rows, "entity renderer", client.getAsJsonArray("entity_renderers"), "ms_per_frame", "ms/frame");
        JsonObject particles = client.getAsJsonObject("particles");
        addTop(rows, "particle render", particles.getAsJsonArray("renders"), "ms_per_frame", "ms/frame");
        addTop(rows, "particle tick", particles.getAsJsonArray("ticks"), "ms_per_tick", "ms/t");
        addTop(rows, "stage", client.getAsJsonArray("render_stages"), "ms_per_frame", "ms/frame");
        addTop(rows, "client be", client.getAsJsonArray("block_entity_ticks"), "ms_per_tick", "ms/t");
        addTop(rows, "in", network.getAsJsonArray("clientbound"), "per_s", "/s");
        addTop(rows, "out", network.getAsJsonArray("serverbound"), "per_s", "/s");
        return rows;
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

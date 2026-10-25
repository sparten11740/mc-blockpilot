package dev.blockpilot.game.observe;

import com.mojang.blaze3d.platform.NativeImage;
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
import dev.blockpilot.game.Rel;
import dev.blockpilot.game.Screenshots;
import dev.blockpilot.game.sable.SableSupport;
import dev.blockpilot.protocol.CommandFailure;
import dev.blockpilot.protocol.CommandRegistry;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Queries over the sound, message and particle logs. */
public final class ObservationCommands {
    private static final int MAX_BURST = 60;

    public void register(CommandRegistry registry) {
        registry.register(CommandSpec.action("screenshot", this::screenshot).group("observe")
            .optionalArg("width", ArgType.INT).optionalKeyword("sheet", "sheet")
            .option("burst", "number of frames (max 60)", ArgType.INT)
            .option("every", "ticks between burst frames, e.g. 10t", ArgType.DURATION)
            .option("hud", "off: hide the GUI, crosshair and hand while capturing",
                List.of("on", "off"))
            .option("crop", "x0,y0,x1,y1: only that part of each frame, in fractions from the top left")
            .help("saves the last rendered frame as PNG (default 512 wide) and returns its path; with burst=n "
                + "every=Nt it saves n frames, one every N ticks, starting now, and with sheet one PNG of them in a "
                + "grid, each labelled #<n> t<tick>. Works with the window in the background. MCP also has a "
                + "screenshot tool").field("file", "absolute path")
            .example("screenshot burst=8 every=10t sheet crop=0.25,0.25,0.75,0.75"));
        registry.register(CommandSpec.action("camera", this::camera).group("observe")
            .form(form -> form.keyword("op", "at").arg("pos", ArgType.POSITION)
                .optional(look -> look.keyword("look", "look").arg("target", ArgType.POSITION, ArgType.ENTITY)))
            .form(form -> form.keyword("op", "reset"))
            .help("camera at <pos> [look <pos|e<id>>]: renders from that point (the player stays visible); "
                + "camera reset: back to the player's view. The player keeps acting normally")
            .example("camera at ~10,5,10 look ~0,1,0"));
        registry.register(CommandSpec.query("sounds", this::sounds).group("observe")
            .option("since", "first tick to include (use the tick of a previous reply)", ArgType.INT)
            .option("match", "sound id or pattern, e.g. aerolarge:* or *thunder*", ArgType.PATTERN,
                ArgType.ID)
            .option("limit", "most recent n (default 50)", ArgType.INT)
            .help("sounds the client started: id, category, position, volume, pitch, tick (also when muted)")
            .field("tick", "current tick, for the next since=").field("count", "matching sounds")
            .example("sounds since=1200 match=*gear*"));
        registry.register(CommandSpec.query("messages", this::messages).group("observe")
            .option("since", "first tick to include", ArgType.INT)
            .option("kind", "which messages", List.of("chat", "actionbar", "title", "subtitle"))
            .option("limit", "most recent n (default 50)", ArgType.INT)
            .help("chat, action-bar, title and subtitle messages with their tick").field("tick", "current tick")
            .field("count", "matching messages").example("messages kind=actionbar"));
        registry.register(CommandSpec.query("particles", this::particles).group("observe")
            .option("since", "first tick to include (default: the last 20 ticks)", ArgType.INT)
            .help("particles spawned per type over a window (the last 1200 ticks are kept)")
            .field("tick", "current tick").field("total", "all particles in the window").example("particles since=1200"));
    }

    private Action screenshot(CommandContext context, Args args) throws CommandException {
        int frames = Mth.clamp(args.intOption("burst", 1), 1, MAX_BURST);
        boolean sheet = args.has("sheet");
        if (sheet && frames < 2) {
            throw new CommandException("syntax", "a sheet needs burst=<n> of at least 2");
        }
        int width = sheet ? ContactSheet.tileWidth(frames, args.has("width") ? args.integer("width", 0) : null)
            : Mth.clamp(args.integer("width", 512), 16, 1280);
        FrameRect crop = args.hasOption("crop") ? FrameRect.parse(args.option("crop")) : FrameRect.ALL;
        Duration every = args.durationOption("every");
        int interval = every == null ? 10 : Math.max(1, every.ticks());
        boolean hideHud = "off".equals(args.option("hud"));
        var options = Minecraft.getInstance().options;
        boolean hudBefore = options.hideGui;
        List<Row> rows = new ArrayList<>();
        List<NativeImage> tiles = new ArrayList<>();
        // With the HUD hidden, the first capture waits a tick so a frame without it has been rendered.
        int delay = hideHud ? 1 : 0;
        int[] elapsed = {0};
        return new Action() {
            @Override
            public Result tick() throws CommandException {
                if (hideHud) {
                    options.hideGui = true;
                }
                int tick = elapsed[0]++;
                try {
                    if (tick >= delay && (tick - delay) % interval == 0) {
                        capture();
                    }
                    if (rows.size() < frames) {
                        return null;
                    }
                    Fields fields = new Fields();
                    if (sheet) {
                        List<String> labels = rows.stream().map(row -> ContactSheet.label(Integer.parseInt(row.key()),
                            (Long) row.fields().get("tick"))).toList();
                        fields.bare("file", Screenshots.save(Screenshots.sheet(tiles, labels), "_sheet").toString());
                    }
                    cancel();
                    if (frames == 1) {
                        return Result.ok(new Fields().bare("file", rows.get(0).fields().get("file")));
                    }
                    return Result.ok(fields.put("frames", rows.size(), "frames=" + rows.size())).withRows(rows);
                } catch (IOException e) {
                    cancel();
                    throw new CommandException("internal", "screenshot failed: " + e.getMessage());
                }
            }

            /** One frame: saved as its own file, or kept for the sheet. */
            private void capture() throws IOException {
                long tick = Observations.tick();
                Fields fields = new Fields();
                NativeImage frame = Screenshots.frame(width, crop);
                if (sheet) {
                    tiles.add(frame);
                } else {
                    try (frame) {
                        Path file = Screenshots.save(frame.asByteArray(), frames > 1 ? "_" + rows.size() : "");
                        fields.bare("file", file.toString());
                    }
                }
                rows.add(Row.of(String.valueOf(rows.size()), fields.put("tick", tick, "t" + tick)));
            }

            @Override
            public void cancel() {
                if (hideHud) {
                    options.hideGui = hudBefore;
                }
                tiles.forEach(NativeImage::close);
                tiles.clear();
            }

            @Override
            public String label() {
                return "screenshot";
            }
        };
    }

    private Action camera(CommandContext context, Args args) throws CommandException {
        if (args.word("op").equals("reset")) {
            CameraControl.reset();
            return () -> Result.ok();
        }
        Level level = Minecraft.getInstance().level;
        Position at = context.resolve(args.position("pos"));
        Vec3 position = new Vec3(at.x(), at.y(), at.z());
        CameraControl.Point target;
        if (!args.has("target")) {
            var player = Minecraft.getInstance().player;
            target = partialTick -> player.getEyePosition();
        } else if (args.type("target") == ArgType.ENTITY) {
            Entity entity = level.getEntity(args.entity("target").id());
            if (entity == null) {
                throw new CommandException("no_entity", args.word("target"));
            }
            target = partialTick -> entity.isRemoved() ? null : entity.getBoundingBox().getCenter();
        } else {
            Position look = context.resolve(args.position("target"));
            target = rendered(level, new Vec3(look.x(), look.y(), look.z()));
        }
        CameraControl.place(new CameraControl.Placement(rendered(level, position), target));
        // Absolute positions: relative ones would be relative to the player, who may be far away or falling.
        Position atAbsolute = Rel.absolute(position);
        return ActionSupport.afterFrames(2, () -> {
            Vec3 looking = target.at(1);
            Fields fields = new Fields().put("camera", atAbsolute, "camera " + atAbsolute);
            if (looking != null) {
                fields.put("looking", Rel.absolute(looking), "looking " + Rel.absolute(looking));
            }
            return Result.ok(fields);
        });
    }

    /** The point as it is, or for a point in a sub-level's plot, where the client draws it in each frame. */
    private static CameraControl.Point rendered(Level level, Vec3 point) {
        if (SableSupport.subLevels().at(level, BlockPos.containing(point)) == null) {
            return partialTick -> point;
        }
        Vec3[] last = {point};
        return partialTick -> {
            try {
                last[0] = SableSupport.subLevels().toWorldRendered(level, point, partialTick);
            } catch (CommandFailure e) {
                // Thrown on the render thread, where it must not escape; the camera stays where it was last drawn.
            }
            return last[0];
        };
    }

    private static long since(Args args, long fallback) throws CommandException {
        String value = args.option("since");
        try {
            return value == null ? fallback : Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new CommandException("syntax", "since= expects a tick number");
        }
    }

    private static Pattern glob(String pattern) {
        String id = pattern.contains(":") || pattern.startsWith("*") ? pattern : "minecraft:" + pattern;
        return Pattern.compile(("\\Q" + id + "\\E").replace("*", "\\E.*\\Q"));
    }

    private static <T> List<T> last(List<T> items, int limit) {
        return items.size() <= limit ? items : items.subList(items.size() - limit, items.size());
    }

    private Result sounds(CommandContext context, Args args) throws CommandException {
        long since = since(args, Long.MIN_VALUE);
        Pattern match = args.option("match") == null ? null : glob(args.option("match"));
        List<Observations.Sound> sounds = new ArrayList<>();
        for (Observations.Sound sound : Observations.sounds(since)) {
            String full = sound.id().contains(":") ? sound.id() : "minecraft:" + sound.id();
            if (match == null || match.matcher(full).matches()) {
                sounds.add(sound);
            }
        }
        List<Observations.Sound> shown = last(sounds, args.intOption("limit", 50));
        List<Row> rows = new ArrayList<>();
        for (Observations.Sound sound : shown) {
            Fields fields = new Fields().bare("id", sound.id()).bare("category", sound.category());
            if (sound.ui()) {
                fields.put("ui", true, "ui");
            } else {
                fields.bare("at", Rel.of(sound.position())).hidden("at_abs", Rel.absolute(sound.position()));
            }
            fields.put("volume", (double) sound.volume(), "vol" + Numbers.format(sound.volume()))
                .put("pitch", (double) sound.pitch(), "pitch" + Numbers.format(sound.pitch()))
                .put("tick", sound.tick(), "t" + sound.tick());
            rows.add(Row.of(sound.tick() + ":" + sound.id(), fields));
        }
        return Result.ok(new Fields().put("tick", Observations.tick(), "tick=" + Observations.tick())
            .put("count", sounds.size(), "count=" + sounds.size())).withRows(rows, sounds.size() - shown.size());
    }

    private Result messages(CommandContext context, Args args) throws CommandException {
        long since = since(args, Long.MIN_VALUE);
        String kind = args.option("kind");
        List<Observations.Message> messages = new ArrayList<>();
        for (Observations.Message message : Observations.messages(since)) {
            if (kind == null || kind.equals(message.kind())) {
                messages.add(message);
            }
        }
        List<Observations.Message> shown = last(messages, args.intOption("limit", 50));
        List<Row> rows = new ArrayList<>();
        for (Observations.Message message : shown) {
            rows.add(Row.of(message.tick() + ":" + message.kind(), new Fields().bare("kind", message.kind())
                .put("tick", message.tick(), "t" + message.tick()).bare("text", message.text())));
        }
        return Result.ok(new Fields().put("tick", Observations.tick(), "tick=" + Observations.tick())
            .put("count", messages.size(), "count=" + messages.size())).withRows(rows, messages.size() - shown.size());
    }

    private Result particles(CommandContext context, Args args) throws CommandException {
        long since = since(args, Observations.tick() - 20);
        Map<String, Integer> counts = Observations.particles(since);
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(counts.entrySet());
        sorted.sort(Map.Entry.<String, Integer>comparingByValue().reversed());
        int total = 0;
        List<Row> rows = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : sorted) {
            total += entry.getValue();
            rows.add(Row.of(entry.getKey(), new Fields().bare("type", entry.getKey())
                .put("count", entry.getValue(), "x" + entry.getValue())));
        }
        return Result.ok(new Fields().put("tick", Observations.tick(), "tick=" + Observations.tick())
            .put("total", total, "total=" + total)).withRows(rows);
    }
}

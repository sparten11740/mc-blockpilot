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
import dev.blockpilot.game.FrameCapture;
import dev.blockpilot.game.Ids;
import dev.blockpilot.game.Rel;
import dev.blockpilot.game.Screenshots;
import dev.blockpilot.game.Sides;
import dev.blockpilot.game.input.KeyNames;
import dev.blockpilot.game.sable.SableSupport;
import dev.blockpilot.game.sable.SubLevels;
import dev.blockpilot.mixin.SoundEngineAccessor;
import dev.blockpilot.mixin.SoundManagerAccessor;
import dev.blockpilot.protocol.CommandFailure;
import dev.blockpilot.protocol.CommandRegistry;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Queries over the sound, message and particle logs. */
public final class ObservationCommands {
    private static final int MAX_BURST = 60;
    /** How far {@code camera at … clear} moves the camera at most, and in what steps, in blocks. */
    private static final double MAX_CLEAR = 8;
    private static final double CLEAR_STEP = 0.25;

    public void register(CommandRegistry registry) {
        registry.register(CommandSpec.action("screenshot", this::screenshot).group("observe")
            .optionalArg("width", ArgType.INT).optionalKeyword("sheet", "sheet")
            .option("burst", "number of frames (max 60)", ArgType.INT)
            .option("every", "ticks between burst frames, e.g. 10t", ArgType.DURATION)
            .option("hud", "off: hide the GUI, crosshair and hand while capturing",
                List.of("on", "off"))
            .option("crop", "x0,y0,x1,y1: only that part of each frame, in fractions from the top left")
            .help("saves the last rendered frame as PNG (default 512 wide, max 1280 and the frame's or crop's own "
                + "width) and returns its path and the tick it was captured on; with burst=n every=Nt it saves n "
                + "frames, one every N ticks, starting now, and with sheet one PNG of them in a grid, each labelled "
                + "#<n> t<tick>, the width being each tile's (cut so the sheet is at most 1536 wide). A frame rendered "
                + "from a placed camera (camera at, until camera reset) carries camera <x,y,z>. Works with the "
                + "window in the background. MCP also has a screenshot tool").field("file", "absolute path")
            .field("tick", "the tick the frame was captured on")
            .field("camera", "the placed camera's absolute position, for a frame rendered from it")
            .field("width", "the width the frames have, when narrower than asked (or than 512)")
            .example("screenshot burst=8 every=10t sheet crop=0.25,0.25,0.75,0.75"));
        registry.register(CommandSpec.action("camera", this::camera).group("observe")
            .form(form -> form.keyword("op", "at").arg("pos", ArgType.POSITION)
                .optional(look -> look.keyword("look", "look").arg("target", ArgType.POSITION, ArgType.ENTITY))
                .optionalKeyword("clear", "clear"))
            .form(form -> form.keyword("op", "reset", "off"))
            .help("camera at <pos> [look <pos|e<id>>] [clear]: renders from that point (the player stays visible); "
                + "with Sable, a plot position, for the point or the look, rides along with its sub-level each frame; "
                + "clear: when the point is in a block with a collision shape, moves the camera toward the look point "
                + "(the player's eyes without look), a quarter block at a time, at most 8 blocks and never past it, to "
                + "the first point clear of such blocks; "
                + "camera reset (or off): back to the player's view. The player keeps acting normally. Chunks load "
                + "around the player, so a camera beyond the render distance shows sky. To take in an effect that "
                + "spans hundreds of blocks, widen the view with option fov 110 and clear it with option renderClouds "
                + "off (option reset puts both back)")
            .field("block", "the block the camera sits in").field("inside", "set when that block is solid")
            .field("unloaded", "set instead of block when the client hasn't loaded the camera's chunk")
            .field("cleared", "clear: how far the camera moved, in blocks, when it had to")
            .field("uncleared", "clear: set when no clear point was in reach, so the camera stayed at the point")
            .example("camera at ~10,5,10 look ~0,1,0"));
        registry.register(CommandSpec.action("settle", this::settle).needsWorld().group("observe")
            .help("waits until the chunks within the view distance of the player have arrived from the server and "
                + "those in view have compiled, as option does after a change: after camera at, a teleport or "
                + "anything else that brings new chunks into view, before a screenshot. Answers settled=<s>, or "
                + "unsettled pending=<n> at timeout= (default 20s). New terrain can take the server many seconds to "
                + "generate")
            .field("settled", "seconds until the chunks had arrived and compiled")
            .field("unsettled", "set at the timeout").field("pending", "sections still to compile at the timeout")
            .field("missing", "chunks still to arrive at the timeout, when there are any")
            .example("settle timeout=30s"));
        registry.register(CommandSpec.query("sounds", this::sounds).group("observe")
            .form(form -> form.optionalKeyword("playing", "playing"))
            .option("since", "first tick to include (use the tick of a previous reply), or a duration back from "
                + "now, e.g. 60t", ArgType.INT, ArgType.DURATION)
            .option("match", "sound id or pattern, e.g. mymod:* or *thunder*; an id without * must be a sound "
                + "event the client knows", ArgType.PATTERN, ArgType.ID)
            .option("limit", "most recent n (default 50)", ArgType.INT)
            .help("sounds the client started: id, category, position, volume, pitch, tick (also when muted). vol and "
                + "pitch are what it plays with: the values it was played with times those its resource (sounds.json) "
                + "gives the variant chosen, and where these differ asked:<vol>/<pitch> gives the values it was played "
                + "with; the engine plays pitch within 0.5 to 2. The log "
                + "keeps the last 2000, and from=<tick> says where it begins when it dropped some of the asked "
                + "window. sounds playing: the sounds playing now, with loop and played=<s> since their start (game "
                + "time); a sound whose category or the master volume is 0 never plays, so the log has it and this "
                + "doesn't. A match= without * is a sound event id (minecraft: when it names no namespace); one the "
                + "client doesn't know fails, naming the closest. A position is relative to where the player stands "
                + "now, not where it stood when the sound started")
            .field("tick", "current tick, for the next since=").field("count", "matching sounds")
            .field("at_abs", "JSON: the absolute position the sound started at, or with playing where it is now")
            .field("from", "the tick of the oldest start the log keeps, when it dropped some from the window")
            .field("window", "the ticks the reply covers: from since= (or the oldest start), or from where the log "
                + "still begins when it dropped older starts, to now")
            .field("joined", "the tick this world was joined, when since= is a tick from before it")
            .field("asked_volume", "the volume it was played with, where its resource scales it")
            .field("asked_pitch", "the pitch it was played with, where its resource scales it")
            .field("loop", "playing: the sound loops").field("played", "playing: seconds since it started")
            .example("sounds since=1200 match=*gear*"));
        registry.register(CommandSpec.query("messages", this::messages).group("observe")
            .option("since", "first tick to include, or a duration back from now, e.g. 60t", ArgType.INT,
                ArgType.DURATION)
            .option("kind", "which messages", List.of("chat", "actionbar", "title", "subtitle"))
            .option("match", "only messages whose whole text fits this pattern, ignoring case; * stands for any run "
                + "of characters, spaces included, e.g. *gear*refused*")
            .option("limit", "most recent n (default 50)", ArgType.INT)
            .help("chat, action-bar, title and subtitle messages with their tick; the log keeps the last 500, and "
                + "from=<tick> says where it begins when it dropped some of the asked window")
            .field("tick", "current tick")
            .field("from", "the tick of the oldest message the log keeps, when it dropped some from the window")
            .field("window", "the ticks the reply covers: from since= (or the oldest message), or from where the log "
                + "still begins when it dropped older messages, to now")
            .field("joined", "the tick this world was joined, when since= is a tick from before it")
            .field("count", "matching messages").example("messages kind=actionbar"));
        registry.register(CommandSpec.query("particles", this::particles).group("observe")
            .option("since", "first tick to include, or a duration back from now (default: the last 20 ticks)",
                ArgType.INT, ArgType.DURATION)
            .help("particles spawned per type over a window (the counts of the last 1200 ticks that had any are "
                + "kept); window=t<first>..t<now> names the ticks the reply covers")
            .field("tick", "current tick").field("total", "all particles in the window")
            .field("window", "the ticks the reply covers: from since= (or the default), or from where the log still "
                + "begins when it dropped older counts, to now")
            .field("joined", "the tick this world was joined, when since= is a tick from before it")
            .example("particles since=1200"));
    }

    private Action screenshot(CommandContext context, Args args) throws CommandException {
        int frames = Mth.clamp(args.intOption("burst", 1), 1, MAX_BURST);
        boolean sheet = args.has("sheet");
        if (sheet && frames < 2) {
            throw new CommandException("syntax", "a sheet needs burst=<n> of at least 2");
        }
        int asked = args.integer("width", 512);
        int width = sheet ? ContactSheet.tileWidth(frames, args.has("width") ? asked : null)
            : Mth.clamp(asked, 16, 1280);
        FrameRect crop = args.hasOption("crop") ? FrameRect.parse(args.option("crop")) : FrameRect.ALL;
        Duration every = args.durationOption("every");
        int interval = every == null ? 10 : Math.max(1, every.ticks());
        boolean hideHud = "off".equals(args.option("hud"));
        var options = Minecraft.getInstance().options;
        boolean hudBefore = options.hideGui;
        List<Row> rows = new ArrayList<>();
        List<NativeImage> tiles = new ArrayList<>();
        // The first capture is of a frame rendered after this request, so it shows what the commands before it did
        // (and, with the HUD hidden, no HUD). A frame renders after its ticks, so by any tick of a later frame the
        // last rendered frame is such a frame.
        long requested = Screenshots.frames();
        int[] elapsed = {-1};
        return new Action() {
            private int captured;

            @Override
            public Result tick() throws CommandException {
                if (hideHud) {
                    options.hideGui = true;
                }
                if (elapsed[0] < 0 && Screenshots.frames() == requested) {
                    return null;
                }
                int tick = ++elapsed[0];
                try {
                    if (tick % interval == 0) {
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
                    Result result;
                    if (frames == 1) {
                        Fields frame = rows.get(0).fields();
                        result = Result.ok(CameraControl.withFrameCamera(new Fields().bare("file", frame.get("file"))
                            .put("tick", frame.get("tick"), "t" + frame.get("tick")), (Position) frame.get("camera")));
                    } else {
                        result = Result.ok(fields.put("frames", rows.size(), "frames=" + rows.size())).withRows(rows);
                    }
                    // Cut to the cap, to the frame's or crop's own width, or to fit the sheet.
                    return captured < asked ? result.with(new Fields().put("width", captured)) : result;
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
                captured = frame.getWidth();
                if (sheet) {
                    tiles.add(frame);
                } else {
                    try (frame) {
                        Path file = Screenshots.save(FrameCapture.png(frame), frames > 1 ? "_" + rows.size() : "");
                        fields.bare("file", file.toString());
                    }
                }
                fields.put("tick", tick, "t" + tick);
                rows.add(Row.of(String.valueOf(rows.size()),
                    CameraControl.withFrameCamera(fields, CameraControl.frameCamera())));
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
        if (!args.word("op").equals("at")) {
            CameraControl.reset();
            return () -> Result.ok();
        }
        Level level = Minecraft.getInstance().level;
        Position at = context.resolve(args.position("pos"));
        Vec3 asked = new Vec3(at.x(), at.y(), at.z());
        CameraControl.Point target;
        Vec3 lookPoint;
        if (!args.has("target")) {
            var player = Minecraft.getInstance().player;
            target = partialTick -> player.getEyePosition();
            lookPoint = player.getEyePosition();
        } else if (args.type("target") == ArgType.ENTITY) {
            Entity entity = level.getEntity(args.entity("target").id());
            if (entity == null) {
                throw new CommandException("no_entity", args.word("target"));
            }
            target = partialTick -> entity.isRemoved() ? null : entity.getBoundingBox().getCenter();
            lookPoint = entity.getBoundingBox().getCenter();
        } else {
            Position look = context.resolve(args.position("target"));
            lookPoint = new Vec3(look.x(), look.y(), look.z());
            target = rendered(level, lookPoint);
        }
        Vec3 clear = args.has("clear") ? clearPoint(level, asked, inFrameOf(level, asked, lookPoint)) : asked;
        Vec3 position = clear == null ? asked : clear;
        CameraControl.place(new CameraControl.Placement(rendered(level, position), target));
        // Absolute positions: relative ones would be relative to the player, who may be far away or falling.
        Position atAbsolute = Rel.absolute(position);
        BlockPos cell = BlockPos.containing(position);
        return ActionSupport.afterFrames(2, () -> {
            Vec3 looking = target.at(1);
            Fields fields = new Fields().put("camera", atAbsolute, "camera " + atAbsolute);
            if (clear == null) {
                fields.put("uncleared", true, "uncleared");
            } else if (!clear.equals(asked)) {
                fields.put("cleared", Math.round(clear.distanceTo(asked) * 100) / 100.0);
            }
            if (!Sides.loaded(level, cell)) {
                // A missing chunk reads as air, but renders as nothing.
                fields.put("unloaded", true, "unloaded");
            } else {
                BlockState block = level.getBlockState(cell);
                fields.put("block", Ids.of(block), "in:" + Ids.of(block));
                if (block.isCollisionShapeFullBlock(level, cell)) {
                    // The view starts inside a solid block, which shows its faces from within or nothing at all.
                    fields.put("inside", Ids.of(block));
                }
            }
            if (looking != null) {
                fields.put("looking", Rel.absolute(looking), "looking " + Rel.absolute(looking));
            }
            return Result.ok(fields);
        });
    }

    private Action settle(CommandContext context, Args args) throws CommandException {
        return new Settle("settle", Fields::new, args.durationOption("timeout"));
    }

    /**
     * The first point from {@code from} toward {@code to}, a quarter block at a time, whose cell has no collision
     * shape: at most {@link #MAX_CLEAR} blocks on and never past {@code to}; null when there is none.
     */
    private static Vec3 clearPoint(Level level, Vec3 from, Vec3 to) {
        double reach = Math.min(MAX_CLEAR, from.distanceTo(to));
        Vec3 direction = reach > 0 ? to.subtract(from).normalize() : Vec3.ZERO;
        for (double moved = 0; moved <= reach; moved += CLEAR_STEP) {
            Vec3 point = from.add(direction.scale(moved));
            BlockPos cell = BlockPos.containing(point);
            if (level.getBlockState(cell).getCollisionShape(level, cell).isEmpty()) {
                return point;
            }
        }
        return null;
    }

    /**
     * The look point in the camera point's frame, so that {@link #clearPoint} steps along the real view line: the plot
     * of the camera point's sub-level, else the world. As given when that sub-level is gone.
     */
    private static Vec3 inFrameOf(Level level, Vec3 camera, Vec3 look) {
        SubLevels subLevels = SableSupport.subLevels();
        SubLevels.Info cameraSubLevel = subLevels.at(level, BlockPos.containing(camera));
        SubLevels.Info lookSubLevel = subLevels.at(level, BlockPos.containing(look));
        if (cameraSubLevel != null && lookSubLevel != null && cameraSubLevel.id().equals(lookSubLevel.id())) {
            return look;
        }
        Vec3 world = subLevels.toWorld(level, look);
        if (cameraSubLevel == null) {
            return world;
        }
        Vec3 plot = subLevels.toPlot(level, cameraSubLevel.id(), world);
        return plot == null ? look : plot;
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
        return Since.tick(args, Observations.tick(), fallback);
    }

    private static Pattern glob(String pattern) {
        return TextPattern.compile(pattern.contains(":") || pattern.startsWith("*") ? pattern : "minecraft:" + pattern);
    }

    private static <T> List<T> last(List<T> items, int limit) {
        return items.size() <= limit ? items : items.subList(items.size() - limit, items.size());
    }

    private Result sounds(CommandContext context, Args args) throws CommandException {
        long since = since(args, Long.MIN_VALUE);
        Pattern match = args.option("match") == null ? null : soundPattern(args.option("match"));
        int limit = args.intOption("limit", 50);
        if (args.has("playing")) {
            return playing(since, match, limit).with(joined(args));
        }
        List<Observations.Sound> sounds = new ArrayList<>();
        for (Observations.Sound sound : Observations.sounds(since)) {
            if (matches(match, sound.id())) {
                sounds.add(sound);
            }
        }
        List<Observations.Sound> shown = last(sounds, limit);
        List<Row> rows = new ArrayList<>();
        for (Observations.Sound sound : shown) {
            Fields fields = soundFields(sound).put("tick", sound.tick(), "t" + sound.tick());
            rows.add(Row.of(sound.tick() + ":" + sound.id(), fields));
        }
        Long from = Observations.soundsFrom(since);
        return Result.ok(window(listed(sounds.size(), from), Since.first(since, from, Observations.soundsOldest(),
            Observations.tick()))).with(joined(args)).withRows(rows, sounds.size() - shown.size());
    }

    private record Playing(SoundInstance sound, String id, Long started) {
    }

    /** The sounds whose channel is still playing; the engine keeps a stopped one in its map for a few ticks. */
    private Result playing(long since, Pattern match, int limit) {
        var engine = ((SoundManagerAccessor) Minecraft.getInstance().getSoundManager()).blockpilot$soundEngine();
        List<Playing> playing = new ArrayList<>();
        ((SoundEngineAccessor) engine).blockpilot$channels().forEach((sound, channel) -> {
            String id = Ids.of(sound.getLocation());
            Long started = Observations.started(sound);
            boolean inWindow = started == null ? since == Long.MIN_VALUE : started >= since;
            if (!channel.isStopped() && inWindow && matches(match, id)) {
                playing.add(new Playing(sound, id, started));
            }
        });
        playing.sort(Comparator.comparing(Playing::started, Comparator.nullsFirst(Comparator.naturalOrder())));
        List<Playing> shown = last(playing, limit);
        long now = Observations.tick();
        List<Row> rows = new ArrayList<>();
        for (Playing entry : shown) {
            SoundInstance sound = entry.sound();
            Fields fields = soundFields(Observations.Sound.of(now, sound));
            if (sound.isLooping()) {
                fields.put("loop", true, "loop");
            }
            if (entry.started() != null) {
                double played = new Duration((int) Math.max(0, now - entry.started())).seconds();
                fields.put("tick", entry.started(), "t" + entry.started())
                    .put("played", played, "played=" + Numbers.format(played) + "s");
            }
            rows.add(Row.of((entry.started() == null ? "" : entry.started() + ":") + entry.id(), fields));
        }
        return Result.ok(listed(playing.size(), null)).withRows(rows, playing.size() - shown.size());
    }

    private static Fields soundFields(Observations.Sound sound) {
        Fields fields = new Fields().bare("id", sound.id()).bare("category", sound.category());
        if (sound.ui()) {
            fields.put("ui", true, "ui");
        } else {
            fields.bare("at", Rel.of(sound.position())).hidden("at_abs", Rel.absolute(sound.position()));
        }
        fields.put("volume", (double) sound.volume(), "vol" + Numbers.format(sound.volume()))
            .put("pitch", (double) sound.pitch(), "pitch" + Numbers.format(sound.pitch()));
        Float askedVolume = sound.askedVolume();
        Float askedPitch = sound.askedPitch();
        if (askedVolume != null && (askedVolume != sound.volume() || askedPitch != sound.pitch())) {
            fields.put("asked_volume", (double) askedVolume, "asked:" + Numbers.format(askedVolume) + "/"
                + Numbers.format(askedPitch)).hidden("asked_pitch", (double) askedPitch);
        }
        return fields;
    }

    /** A sound's match= as a pattern; without * it names a sound event, which the client has to know. */
    private static Pattern soundPattern(String match) throws CommandException {
        if (match.contains("*")) {
            return glob(match);
        }
        String id = match.contains(":") ? match : "minecraft:" + match;
        Set<String> known = Minecraft.getInstance().getSoundManager().getAvailableSounds().stream()
            .map(Object::toString).collect(Collectors.toSet());
        if (known.contains(id)) {
            return glob(id);
        }
        String path = id.substring(id.indexOf(':') + 1);
        List<String> containing = known.stream().filter(candidate -> candidate.contains(path)).toList();
        throw new CommandException("syntax", "unknown sound " + id + "; closest: "
            + String.join(" ", KeyNames.closest(id, containing.isEmpty() ? known : containing, 3)) + "; or match=*"
            + path + "*");
    }

    private static boolean matches(Pattern match, String id) {
        return match == null || match.matcher(id.contains(":") ? id : "minecraft:" + id).matches();
    }

    /** Flags a bare {@code since=} tick from before this world was joined, most likely meant as ticks back. */
    private static Fields joined(Args args) {
        Long joined = Observations.joinedTick();
        if (!Since.predatesJoin(args, joined)) {
            return new Fields();
        }
        String since = args.option("since");
        return new Fields().put("joined", joined, "joined=t" + joined + " (since=" + since + " is a tick; " + since
            + "t counts back)");
    }

    /** {@code window=t<first>..t<now>}: the ticks a reply covers, from the first one to the current one. */
    private static Fields window(Fields fields, long first) {
        long now = Observations.tick();
        Map<String, Long> window = new LinkedHashMap<>();
        window.put("from", first);
        window.put("to", now);
        return fields.put("window", window, "window=t" + first + "..t" + now);
    }

    /** The current tick and the count, with from= when the log dropped entries from the window. */
    private static Fields listed(int count, Long from) {
        Fields fields = new Fields().put("tick", Observations.tick(), "tick=" + Observations.tick())
            .put("count", count, "count=" + count);
        return from == null ? fields : fields.put("from", from, "from=" + from);
    }

    private Result messages(CommandContext context, Args args) throws CommandException {
        long since = since(args, Long.MIN_VALUE);
        String kind = args.option("kind");
        Pattern match = args.option("match") == null ? null : TextPattern.compile(args.option("match"));
        List<Observations.Message> messages = new ArrayList<>();
        for (Observations.Message message : Observations.messages(since)) {
            if ((kind == null || kind.equals(message.kind()))
                && (match == null || match.matcher(message.text()).matches())) {
                messages.add(message);
            }
        }
        List<Observations.Message> shown = last(messages, args.intOption("limit", 50));
        List<Row> rows = new ArrayList<>();
        for (Observations.Message message : shown) {
            rows.add(Row.of(message.tick() + ":" + message.kind(), new Fields().bare("kind", message.kind())
                .put("tick", message.tick(), "t" + message.tick()).bare("text", message.text())));
        }
        Long from = Observations.messagesFrom(since);
        return Result.ok(window(listed(messages.size(), from), Since.first(since, from,
            Observations.messagesOldest(), Observations.tick()))).with(joined(args))
            .withRows(rows, messages.size() - shown.size());
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
        Fields fields = new Fields().put("tick", Observations.tick(), "tick=" + Observations.tick())
            .put("total", total, "total=" + total);
        return Result.ok(window(fields, Since.first(since, Observations.particlesFrom(since),
            Observations.particlesOldest(), Observations.tick()))).with(joined(args)).withRows(rows);
    }
}

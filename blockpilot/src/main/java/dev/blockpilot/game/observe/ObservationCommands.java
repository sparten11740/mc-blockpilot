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
import dev.blockpilot.game.common.FrameCapture;
import dev.blockpilot.game.common.Ids;
import dev.blockpilot.game.common.Sides;
import dev.blockpilot.game.input.KeyNames;
import dev.blockpilot.game.sable.Rel;
import dev.blockpilot.game.sable.SableSupport;
import dev.blockpilot.game.sable.SubLevels;
import dev.blockpilot.mixin.SoundEngineAccessor;
import dev.blockpilot.mixin.SoundManagerAccessor;
import dev.blockpilot.protocol.CommandFailure;
import dev.blockpilot.protocol.CommandRegistry;
import dev.blockpilot.protocol.Engine;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
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
    private static final int MAX_VIA = 32;
    /** How long camera at … away waits for the client to get the camera's chunk before it answers. */
    private static final int AWAY_CHUNK_TICKS = 200;
    private static final List<String> SOUND_SOURCES = Arrays.stream(SoundSource.values()).map(SoundSource::getName)
        .toList();

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
                .optionalKeyword("clear", "clear").optionalKeyword("away", "away"))
            .form(form -> form.keyword("op", "move").arg("pos", ArgType.POSITION)
                .repeated(MAX_VIA, via -> via.keyword("through", "via").arg("via", ArgType.POSITION))
                .optional(look -> look.keyword("look", "look")
                    .arg("target", List.of("ahead"), ArgType.POSITION, ArgType.ENTITY))
                .keyword("timing", "over").arg("duration", ArgType.DURATION)
                .optionalKeyword("background", "background"))
            .form(form -> form.keyword("op", "reset", "off", "wait"))
            .option("ease", "camera move: how the speed changes along the path; linear (the default) keeps it, in "
                + "starts slowly, out ends slowly, inout does both", List.of("linear", "in", "out", "inout"))
            .help("camera at <pos> [look <pos|e<id>>] [clear]: renders from that point (the player stays visible); "
                + "an entity look target is read where the entity is drawn each frame; "
                + "clear: when the point is in a block with a collision shape, moves the camera toward the look point "
                + "(the player's eyes without look), a quarter block at a time, at most 8 blocks and never past it, to "
                + "the first point clear of such blocks; "
                + "camera move <pos> [via <pos>]… [look <pos|e<id>|ahead>] over <duration> [ease=]: glides the camera "
                + "from where it is drawn now (a placed camera, or the player's eyes) to the point over the duration, "
                + "placed anew in every rendered frame, then leaves it placed there and answers as camera at does; "
                + "through via points (up to 32) in order on one smooth curve with no corner at any, at a constant "
                + "speed along the whole path; without look it keeps looking at what it looked at (from the player's "
                + "view, the way the player looked), with a point or an entity it turns toward it over the move, and "
                + "look ahead looks the way it travels; ease=inout suits a single move from rest, linear (the default) "
                + "keeps the speed across chained moves; it counts client ticks, so it goes on through tick freeze, "
                + "and stop or real input end it where the last frame drew it; with background it answers at once, "
                + "ok moving over=<n>t, and the camera glides on its own while the script and the scripts after it go "
                + "on (server steps, sound, other actions), until a camera at, camera move or camera reset replaces "
                + "it; camera wait answers once a move in the background has ended, as camera at would there, and at "
                + "once with nothing moving; "
                + "with Sable, a plot position, for the point, a via point or the look, rides along with its "
                + "sub-level each frame; status names a placed camera's position as camera <x,y,z>; "
                + "camera reset (or off): back to the player's view. The player keeps acting normally. Chunks load "
                + "around the player, so a camera beyond the render distance shows sky, unless placed with away: in "
                + "singleplayer with commands allowed, camera at … away has the integrated server treat the camera as "
                + "where the player is for the world it loads, ticks and sends (chunks, entities, sounds, particles, "
                + "explosions, playsound), while the player stands still where it is and actions that move it make no "
                + "progress; mobs still spawn and despawn by the player, so summon actors with "
                + "{PersistenceRequired:1b}, and Sable still sends its sub-levels by where the player is, so a camera "
                + "away doesn't show them; it answers once the client has the camera's chunk, after at most 10 s; "
                + "a camera move keeps it away, and camera reset or off, a camera at without "
                + "away, leaving the world, a dimension change or agent control off end it. To take in an effect that "
                + "spans hundreds of blocks, widen the view with option fov 110 and clear it with option renderClouds "
                + "off (option reset puts both back)")
            .field("block", "the block the camera sits in").field("inside", "set when that block is solid")
            .field("unloaded", "set instead of block when the client hasn't loaded the camera's chunk")
            .field("cleared", "clear: how far the camera moved, in blocks, when it had to")
            .field("uncleared", "clear: set when no clear point was in reach, so the camera stayed at the point")
            .field("looking", "the absolute point it looks at, with a look target")
            .field("moving", "camera move … background: set, the move goes on beside the script")
            .field("over", "camera move … background: the move's ticks")
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
                + "now, not where it stood when the sound started. sound plays one on this client")
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
        registry.register(CommandSpec.action("sound", this::sound).group("observe")
            .form(form -> form.arg("id", ArgType.ID)
                .optional(at -> at.keyword("at", "at").arg("pos", ArgType.POSITION)))
            .option("volume", "how loud (default 1); above 1 carries the sound farther, as with playsound",
                ArgType.NUMBER)
            .option("pitch", "0.5 to 2 (default 1), the range the sound engine plays", ArgType.NUMBER)
            .option("source", "the sound category whose volume applies, one of this version's, as option "
                + "soundCategory_<name> names them (default master)", ArgType.WORD)
            .help("plays a sound event on this client only, sending nothing to any server: with at, it starts there "
                + "and fades with distance from the camera as rendered, so a placed camera (camera at) hears it from "
                + "its own seat; without, it plays as an interface sound, the same everywhere. It goes through the "
                + "sound engine like any other sound, so sounds, sounds playing and recordings have it, and the "
                + "engine holds it while the singleplayer game is paused. An id the client doesn't know fails, naming "
                + "the closest. sounds lists the sounds the client started")
            .field("at", "the absolute position it plays at").field("ui", "set for an interface sound")
            .field("tick", "the tick it started")
            .field("muted", "set when its category or the master volume is 0, so it is neither heard nor recorded")
            .example("sound minecraft:block.bell.use at ~0,0,3"));
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
        boolean hudBefore = HudVisibility.hidden();
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
                    HudVisibility.setHidden(true);
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
                    HudVisibility.setHidden(hudBefore);
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
        String op = args.word("op");
        if (args.hasOption("ease") && !op.equals("move")) {
            throw new CommandException("syntax", "ease= is for camera move");
        }
        if (op.equals("move")) {
            return move(context, args);
        }
        if (op.equals("wait")) {
            return new WaitForMove();
        }
        if (!op.equals("at")) {
            CameraControl.reset();
            return () -> Result.ok();
        }
        if (args.has("away")) {
            // The server's gate, checked before anything changes.
            if (Minecraft.getInstance().getSingleplayerServer() == null) {
                throw new CommandException("no_server", "away moves the integrated server's world to the camera");
            }
            if (!Sides.cheats(Minecraft.getInstance().player)) {
                throw new CommandException("no_cheats");
            }
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
        } else {
            target = lookTarget(context, args, level);
            lookPoint = target.at(1);
        }
        Vec3 clear = args.has("clear") ? clearPoint(level, asked, inFrameOf(level, asked, lookPoint)) : asked;
        Vec3 position = clear == null ? asked : clear;
        CameraControl.place(new CameraControl.Placement(rendered(level, position), target));
        if (args.has("away")) {
            AwayCamera.start();
        } else {
            AwayCamera.end();
        }
        Action answer = ActionSupport.afterFrames(2, () -> {
            Fields fields = cameraFields(level, position);
            if (clear == null) {
                fields.put("uncleared", true, "uncleared");
            } else if (!clear.equals(asked)) {
                fields.put("cleared", Math.round(clear.distanceTo(asked) * 100) / 100.0);
            }
            return Result.ok(placedFields(fields, level, position, target));
        });
        if (!args.has("away")) {
            return answer;
        }
        // The server sends the chunks around a camera away from the player only now; answer once its own has come.
        BlockPos cell = BlockPos.containing(position);
        int[] waited = {0};
        return () -> Sides.loaded(level, cell) || waited[0]++ >= AWAY_CHUNK_TICKS ? answer.tick() : null;
    }

    /** A look target: an entity where it is drawn, or a position, which rides along aboard a sub-level. */
    private static CameraControl.Point lookTarget(CommandContext context, Args args, Level level)
        throws CommandException {
        if (args.type("target") == ArgType.ENTITY) {
            Entity entity = level.getEntity(args.entity("target").id());
            if (entity == null) {
                throw new CommandException("no_entity", args.word("target"));
            }
            // Interpolated like the entity's model, so a camera tracking a walking mob turns smoothly.
            return partialTick -> entity.isRemoved() ? null
                : entity.getPosition(partialTick).add(0, entity.getBbHeight() / 2, 0);
        }
        Position look = context.resolve(args.position("target"));
        return rendered(level, new Vec3(look.x(), look.y(), look.z()));
    }

    private Action move(CommandContext context, Args args) throws CommandException {
        int duration = args.duration("duration").ticks();
        if (duration < 1) {
            throw new CommandException("syntax", "over takes 1t or more");
        }
        CameraPath.Ease ease = args.option("ease") == null ? CameraPath.Ease.LINEAR
            : CameraPath.Ease.valueOf(args.option("ease").toUpperCase(Locale.ROOT));
        Level level = Minecraft.getInstance().level;
        var player = Minecraft.getInstance().player;
        CameraControl.Placement before = CameraControl.placement();
        List<CameraControl.Point> points = new ArrayList<>();
        points.add(before != null ? before.position() : player::getEyePosition);
        for (Position via : args.positions("via")) {
            Position point = context.resolve(via);
            points.add(rendered(level, new Vec3(point.x(), point.y(), point.z())));
        }
        Position end = context.resolve(args.position("pos"));
        Vec3 endPoint = new Vec3(end.x(), end.y(), end.z());
        points.add(rendered(level, endPoint));
        CameraMove.Look look = CameraMove.Look.KEEP;
        CameraControl.Point target = null;
        if (args.has("target")) {
            boolean ahead = args.type("target") == ArgType.KEYWORD;
            look = ahead ? CameraMove.Look.AHEAD : CameraMove.Look.TARGET;
            target = ahead ? null : lookTarget(context, args, level);
        }
        boolean background = args.has("background");
        CameraMove move = new CameraMove(points, look, target, before, before == null ? player.getViewVector(1) : null,
            duration, ease, background, placed -> ActionSupport.afterFrames(2, () ->
                Result.ok(placedFields(cameraFields(level, endPoint), level, endPoint, placed.target()))));
        CameraControl.placeMove(move);
        if (background) {
            return () -> Result.ok(new Fields().put("moving", true, "moving").put("over", duration,
                "over=" + duration + "t"));
        }
        return move;
    }

    /** camera wait: answers once no camera move is under way, as camera at answers where the camera then rests. */
    private static final class WaitForMove implements Action, Engine.SelfTimed {
        private Action answer;

        @Override
        public Result tick() throws CommandException {
            if (answer == null) {
                if (CameraControl.moving() != null) {
                    return null;
                }
                CameraControl.Placement placement = CameraControl.placement();
                Level level = Minecraft.getInstance().level;
                Vec3 at = placement == null || level == null ? null : placement.position().at(1);
                answer = at == null ? () -> Result.ok() : ActionSupport.afterFrames(2, () ->
                    Result.ok(placedFields(cameraFields(level, at), level, at, placement.target())));
            }
            return answer.tick();
        }

        @Override
        public String label() {
            return "camera";
        }
    }

    private static Fields cameraFields(Level level, Vec3 position) {
        // Absolute positions: relative ones would be relative to the player, who may be far away or falling.
        Position atAbsolute = Rel.absolute(position);
        return new Fields().put("camera", atAbsolute, "camera " + atAbsolute);
    }

    /** What a placed camera answers after its frames: the block it sits in and, with a target, the point it faces. */
    private static Fields placedFields(Fields fields, Level level, Vec3 position, CameraControl.Point target) {
        BlockPos cell = BlockPos.containing(position);
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
        Vec3 looking = target == null ? null : target.at(1);
        if (looking != null) {
            fields.put("looking", Rel.absolute(looking), "looking " + Rel.absolute(looking));
        }
        return fields;
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

    private Action sound(CommandContext context, Args args) throws CommandException {
        String id = soundId(args.word("id"));
        String unknown = unknownSound(id);
        if (unknown != null) {
            throw new CommandException("syntax", unknown);
        }
        float volume = (float) args.numberOption("volume", 1);
        if (volume < 0) {
            throw new CommandException("syntax", "volume= takes 0 or more, not " + Numbers.format(volume));
        }
        float pitch = (float) args.numberOption("pitch", 1);
        if (pitch < 0.5f || pitch > 2) {
            throw new CommandException("syntax", "pitch= takes 0.5 to 2, the range the sound engine plays, not "
                + Numbers.format(pitch));
        }
        SoundSource source = soundSource(args.option("source"));
        Vec3 at = null;
        if (args.has("pos")) {
            Level level = Minecraft.getInstance().level;
            if (level == null) {
                throw new CommandException("not_in_world", "sound at needs a world");
            }
            Position pos = context.resolve(args.position("pos"));
            // A plot position plays where its sub-level shows that point now; the sound doesn't ride along.
            at = rendered(level, new Vec3(pos.x(), pos.y(), pos.z())).at(1);
        }
        ResourceLocation location = Ids.parse(id);
        Vec3 point = at;
        SoundSource category = source;
        return () -> {
            SoundInstance sound = point == null
                ? new SimpleSoundInstance(location, category, volume, pitch, SoundInstance.createUnseededRandom(),
                    false, 0, SoundInstance.Attenuation.NONE, 0, 0, 0, true)
                : new SimpleSoundInstance(location, category, volume, pitch, SoundInstance.createUnseededRandom(),
                    false, 0, SoundInstance.Attenuation.LINEAR, point.x, point.y, point.z, false);
            Minecraft.getInstance().getSoundManager().play(sound);
            Fields fields = new Fields().bare("id", id).bare("category", category.getName());
            if (point == null) {
                fields.put("ui", true, "ui");
            } else {
                fields.put("at", Rel.absolute(point), "at " + Rel.absolute(point));
            }
            long tick = Observations.tick();
            fields.put("volume", (double) volume, "vol" + Numbers.format(volume))
                .put("pitch", (double) pitch, "pitch" + Numbers.format(pitch)).put("tick", tick, "t" + tick);
            var options = Minecraft.getInstance().options;
            if (options.getSoundSourceVolume(category) == 0 || options.getSoundSourceVolume(SoundSource.MASTER) == 0) {
                fields.put("muted", true, "muted");
            }
            return Result.ok(fields);
        };
    }

    /** The category a source= names; the categories differ between versions, so the grammar takes any word. */
    private static SoundSource soundSource(String name) throws CommandException {
        if (name == null) {
            return SoundSource.MASTER;
        }
        for (SoundSource source : SoundSource.values()) {
            if (source.getName().equals(name)) {
                return source;
            }
        }
        throw new CommandException("syntax", "source= takes " + String.join(" ", SOUND_SOURCES) + ", not " + name);
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
        String id = soundId(match);
        String unknown = unknownSound(id);
        if (unknown != null) {
            throw new CommandException("syntax", unknown + "; or match=*" + id.substring(id.indexOf(':') + 1) + "*");
        }
        return glob(id);
    }

    /** The sound event a name stands for: minecraft: when it names no namespace. */
    private static String soundId(String name) {
        return name.contains(":") ? name : "minecraft:" + name;
    }

    /** Null when the client knows the sound event, else saying it doesn't, with the closest ids it knows. */
    private static String unknownSound(String id) {
        Set<String> known = Minecraft.getInstance().getSoundManager().getAvailableSounds().stream()
            .map(Object::toString).collect(Collectors.toSet());
        if (known.contains(id)) {
            return null;
        }
        String path = id.substring(id.indexOf(':') + 1);
        List<String> containing = known.stream().filter(candidate -> candidate.contains(path)).toList();
        return "unknown sound " + id + "; closest: "
            + String.join(" ", KeyNames.closest(id, containing.isEmpty() ? known : containing, 3));
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

package dev.blockpilot.game.observe;

import dev.blockpilot.api.Action;
import dev.blockpilot.api.ArgType;
import dev.blockpilot.api.Args;
import dev.blockpilot.api.CommandContext;
import dev.blockpilot.api.CommandException;
import dev.blockpilot.api.CommandSpec;
import dev.blockpilot.api.Duration;
import dev.blockpilot.api.Fields;
import dev.blockpilot.api.Lexicon;
import dev.blockpilot.api.Numbers;
import dev.blockpilot.api.Position;
import dev.blockpilot.api.Result;
import dev.blockpilot.game.common.Ids;
import dev.blockpilot.game.common.Sides;
import dev.blockpilot.game.observe.CameraPath.Vec;
import dev.blockpilot.game.sable.Rel;
import dev.blockpilot.game.sable.SableSupport;
import dev.blockpilot.game.sable.SubLevels;
import dev.blockpilot.protocol.CommandRegistry;
import dev.blockpilot.protocol.Engine;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** {@code camera}: places, moves and resets the camera the frames are rendered from. */
final class CameraCommands {
    /** How far {@code camera at … clear} moves the camera at most, and in what steps, in blocks. */
    private static final double MAX_CLEAR = 8;
    private static final double CLEAR_STEP = 0.25;
    private static final int MAX_VIA = 32;
    /** How long camera at … away waits for the client to get the camera's chunk before it answers. */
    private static final int AWAY_CHUNK_TICKS = 200;
    /** What a camera can look at, circle or follow besides a position. */
    private static final ArgType[] MOVING_TARGETS = {ArgType.ENTITY, ArgType.SUB_LEVEL};

    /** Checks a form's arguments as far as they can be before anything changes, and gives what then starts it. */
    @FunctionalInterface
    private interface Prepare {
        CameraTimeline.Starter prepare(CommandContext context, Args args) throws CommandException;
    }

    /**
     * A camera form by its first word: whether it places a camera, taking fov= and roll= and serving as a shot of
     * camera play, and whether it is timed, taking ease=.
     */
    private record Form(String word, boolean placing, boolean timed, Prepare prepare) {
    }

    private final Map<String, Form> forms = table(new Form("at", true, false, this::at),
        new Form("move", true, true, this::move), new Form("follow", true, false, this::follow),
        new Form("orbit", true, true, this::orbit), new Form("play", false, false, this::play),
        new Form("wait", false, false, (context, args) -> WaitForMove::new),
        new Form("reset", false, false, CameraCommands::reset), new Form("off", false, false, CameraCommands::reset));
    private final ObservationCommands.EntityNames names;
    private CommandRegistry registry;

    CameraCommands(ObservationCommands.EntityNames names) {
        this.names = names;
    }

    private static Map<String, Form> table(Form... forms) {
        Map<String, Form> table = new LinkedHashMap<>();
        for (Form form : forms) {
            table.put(form.word(), form);
        }
        return table;
    }

    /** The words of the forms that pass, in order, as a message lists them: {@code at, move, follow and orbit}. */
    private String words(Predicate<Form> which, String conjunction, String... more) {
        List<String> words = new ArrayList<>(forms.values().stream().filter(which).map(Form::word).toList());
        words.addAll(List.of(more));
        return String.join(", ", words.subList(0, words.size() - 1)) + " " + conjunction + " "
            + words.get(words.size() - 1);
    }

    void register(CommandRegistry registry) {
        this.registry = registry;
        registry.register(CommandSpec.action("camera", this::camera).group("observe")
            .form(form -> form.keyword("op", "at").arg("pos", ArgType.POSITION)
                .optional(look -> look.keyword("look", "look").arg("target", ArgType.POSITION, MOVING_TARGETS))
                .optionalKeyword("clear", "clear").optionalKeyword("away", "away"))
            .form(form -> form.keyword("op", "move").arg("pos", ArgType.POSITION)
                .repeated(MAX_VIA, via -> via.keyword("through", "via").arg("via", ArgType.POSITION))
                .optional(look -> look.keyword("look", "look")
                    .arg("target", List.of("ahead"), ArgType.POSITION, MOVING_TARGETS))
                .optional(around -> around.keyword("around", "around").arg("frame", ArgType.SUB_LEVEL))
                .keyword("timing", "over").arg("duration", ArgType.DURATION)
                .optionalKeyword("background", "background"))
            .form(form -> form.keyword("op", "follow").arg("subject", ArgType.ENTITY, ArgType.SUB_LEVEL)
                .optional(at -> at.keyword("offsetting", "at").arg("offset", ArgType.POSITION))
                .optionalKeyword("turning", "turning"))
            .form(form -> form.keyword("op", "orbit").arg("centre", ArgType.POSITION, MOVING_TARGETS)
                .optional(by -> by.keyword("by", "by").arg("degrees", ArgType.NUMBER))
                .keyword("timing", "over").arg("duration", ArgType.DURATION)
                .optionalKeyword("background", "background"))
            .form(form -> form.keyword("op", "play").text("shots"))
            .form(form -> form.keyword("op", "reset", "off", "wait"))
            .option("fov", "camera " + words(Form::placing, "and") + ": the placed camera's vertical field of view, 1 to "
                + "170 degrees, in place of the player's; kept until camera reset", ArgType.NUMBER)
            .option("roll", "camera " + words(Form::placing, "and") + ": tilts the placed camera about the way it "
                + "looks, -180 to 180 degrees, a positive roll leaning it to its right; kept until camera reset",
                ArgType.NUMBER)
            .option("ease", "camera " + words(Form::timed, "and") + ": how the speed changes along the way; linear "
                + "(the default) keeps it, in starts slowly, out ends slowly, inout does both",
                List.of("linear", "in", "out", "inout"))
            .help("camera at <pos> [look <pos|e<id>|sub:<ref>>] [clear]: renders from that point (the player stays "
                + "visible); "
                + "an entity look target is read where the entity is drawn each frame; "
                + "clear: when the point is in a block with a collision shape, moves the camera toward the look point "
                + "(the player's eyes without look), a quarter block at a time, at most 8 blocks and never past it, to "
                + "the first point clear of such blocks; "
                + "camera move <pos> [via <pos>]… [look <pos|e<id>|sub:<ref>|ahead>] [around sub:<ref>] over "
                + "<duration> [ease=]: glides the camera "
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
                + "camera follow <entity|sub:<ref>> [at <~dx,dy,dz>] [turning]: keeps the camera at that offset from "
                + "the "
                + "entity's feet in every frame, looking at it as camera at … look does, until the next camera "
                + "command (stop and real input leave it); without at, the offset the camera has now, so a shot "
                + "framed with camera at … look carries on as the entity walks; with turning, the offset is in the "
                + "entity's frame as Minecraft's ^ coordinates (x to its left, z the way it faces) and turns with it, "
                + "catching up with a quick turn over a second or two; a removed entity leaves the camera where it "
                + "was last drawn; "
                + "camera orbit <pos|entity|sub:<ref>> [by <degrees>] over <duration> [background] [ease=]: circles "
                + "the camera "
                + "round the centre (an entity followed as it moves) from where it is drawn now, keeping its distance "
                + "across and height and looking at the centre, a full turn without by, a positive angle to the "
                + "camera's right as it faces the centre; timed as camera move is (background, camera wait, stop), "
                + "then left where it ended looking at the centre; "
                + "fov= and roll= give a placed camera its own field of view and tilt: at and follow set them at once, "
                + "move and orbit glide to them along the way, a value left out stays, and camera reset returns to "
                + "the player's view; status names the field of view as cam_fov and the roll in cam:; "
                + "with Sable, a plot position, for the point, a via point or the look, rides along with its "
                + "sub-level each frame, and sub:<uuid|name|here> names a sub-level by its origin, drawn each frame: "
                + "as follow's subject (the offset in the world's axes, so the camera keeps its side as the "
                + "sub-level turns; turning is for entities), as orbit's centre, as a look target, and with "
                + "move … around sub:<ref> the move's ~ points, via points and look are offsets in the world's "
                + "axes from that origin in each frame, so a path keeps its shape round a flying sub-level; status "
                + "names a placed camera's position as camera <x,y,z>; "
                + "camera play <shot> [| <shot>]…: plays the shots one after another on the camera's own clock and "
                + "answers at once, shots=<n> over=<ticks>t, while the script and the scripts after it go on; a shot "
                + "is a camera at, move, orbit or follow written without camera, with its own ease=, fov= and roll=, "
                + "or hold <duration>, which keeps the camera as it is; each starts as its command would then, from "
                + "where the camera is drawn, so an at is a cut and a follow lasts until the next shot; ~ positions "
                + "count from the player as each shot starts; all shots are checked before the first starts; camera "
                + "wait answers at the end, or with the error of a shot that couldn't start, and another camera "
                + "command, stop or real input end it; record script with camera wait at its end films it whole; "
                + "camera reset (or off): back to the player's view. The player keeps acting normally. Chunks load "
                + "around the player, so a camera beyond the render distance shows sky, unless placed with away: in "
                + "singleplayer with commands allowed, camera at … away has the integrated server treat the camera as "
                + "where the player is for the world it loads, ticks and sends (chunks, entities, sounds, particles, "
                + "explosions, playsound), while the player stands still where it is and actions that move it make no "
                + "progress; mobs still spawn and despawn by the player, so summon actors with "
                + "{PersistenceRequired:1b}; with Sable, the sub-levels within its tracking range of the camera "
                + "come too, also one unloaded while nobody was near; it answers once the client has the camera's "
                + "chunk, after at most 10 s; "
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
    }

    /** Checks the whole command first, so a refused one leaves the camera, and a play under way, as they were. */
    private Action camera(CommandContext context, Args args) throws CommandException {
        Form form = forms.get(args.word("op"));
        CameraTimeline.Starter start = prepare(form, context, args);
        // camera wait waits for a play; every other form ends it.
        if (!form.word().equals("wait")) {
            CameraTimeline.CAMERA.stop();
        }
        return start.start();
    }

    /** The form's options in range, then its own checks. */
    private CameraTimeline.Starter prepare(Form form, CommandContext context, Args args) throws CommandException {
        if (args.hasOption("ease") && !form.timed()) {
            throw new CommandException("syntax", "ease= is for camera " + words(Form::timed, "and"));
        }
        if ((args.hasOption("fov") || args.hasOption("roll")) && !form.placing()) {
            throw new CommandException("syntax", "fov= and roll= are for camera " + words(Form::placing, "and"));
        }
        // Each form reads the values again as it places the camera.
        fov(args);
        roll(args);
        return form.prepare().prepare(context, args);
    }

    /**
     * camera play: the shots, separated by |, each a camera at, move, orbit or follow without the word camera, or
     * hold for a duration; checked as a whole before the first starts.
     */
    private CameraTimeline.Starter play(CommandContext context, Args args) throws CommandException {
        List<CameraTimeline.Shot> shots = new ArrayList<>();
        int total = 0;
        for (String text : args.text("shots").strip().split("\\s*\\|\\s*")) {
            List<String> tokens = new ArrayList<>(List.of(text.strip().split("\\s+")));
            String op = tokens.get(0);
            if (op.equals("hold")) {
                if (tokens.size() != 2 || !Lexicon.isDuration(tokens.get(1))) {
                    throw new CommandException("syntax", "hold takes a duration, as hold 40t: " + text);
                }
                int ticks = Duration.parse(tokens.get(1)).ticks();
                shots.add(new CameraTimeline.Hold(ticks));
                total += ticks;
                continue;
            }
            Form form = forms.get(op);
            if (form == null || !form.placing()) {
                throw new CommandException("syntax", "a shot is " + words(Form::placing, "or", "hold") + ", not "
                    + text);
            }
            // A timed shot runs in the background, so the next one starts as it ends.
            int over = tokens.indexOf("over");
            if (over >= 0 && over + 1 < tokens.size() && !tokens.contains("background")) {
                tokens.add(over + 2, "background");
            }
            try {
                Args shot = registry.parse("camera " + String.join(" ", tokens));
                shots.add(new CameraTimeline.Cue(prepare(form, context, shot)));
                total += form.timed() ? duration(shot) : 0;
            } catch (CommandException e) {
                throw inShot(e, shots.size() + 1, text);
            }
        }
        int over = total;
        return () -> {
            CameraTimeline.CAMERA.play(shots);
            return () -> Result.ok(new Fields().put("shots", shots.size()).put("over", over, "over=" + over + "t"));
        };
    }

    /** A shot's syntax error, naming the shot; other errors already name what they are about. */
    private static CommandException inShot(CommandException e, int shot, String text) {
        return e.code().equals("syntax")
            ? new CommandException("syntax", "shot " + shot + ", " + text + ": " + e.argument()) : e;
    }

    private static Double fov(Args args) throws CommandException {
        return degrees(args, "fov", 1, 170);
    }

    private static Double roll(Args args) throws CommandException {
        return degrees(args, "roll", -180, 180);
    }

    /** An option in degrees within its range, or null when it is left out. */
    private static Double degrees(Args args, String name, double min, double max) throws CommandException {
        if (!args.hasOption(name)) {
            return null;
        }
        double value = args.numberOption(name, 0);
        if (value < min || value > max) {
            throw new CommandException("syntax", name + "= takes " + Numbers.format(min) + " to " + Numbers.format(max)
                + " degrees, not " + args.option(name));
        }
        return value;
    }

    private static CameraTimeline.Starter reset(CommandContext context, Args args) {
        return () -> {
            CameraControl.reset();
            return () -> Result.ok();
        };
    }

    private CameraTimeline.Starter at(CommandContext context, Args args) throws CommandException {
        if (args.has("away")) {
            if (Minecraft.getInstance().getSingleplayerServer() == null) {
                throw new CommandException("no_server", "away moves the integrated server's world to the camera");
            }
            if (!Sides.cheats(Minecraft.getInstance().player)) {
                throw new CommandException("no_cheats");
            }
        }
        present(args, "target");
        return () -> placeAt(context, args);
    }

    private Action placeAt(CommandContext context, Args args) throws CommandException {
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
            target = point(context, args, "target", level);
            lookPoint = target.at(1);
        }
        Vec3 clear = args.has("clear") ? clearPoint(level, asked, inFrameOf(level, asked, lookPoint)) : asked;
        Vec3 position = clear == null ? asked : clear;
        CameraControl.place(new CameraControl.Placement(CameraControl.drawn(level, position), target)
            .withLens(CameraControl.lensWith(fov(args), roll(args))));
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


    /** Fails with no_entity unless the entity the slot names, when it names one, is loaded now. */
    private void present(Args args, String name) throws CommandException {
        if (args.has(name) && args.type(name) == ArgType.ENTITY) {
            entity(args, name, Minecraft.getInstance().level);
        }
    }

    /** The id of the entity a slot names: e<id>, or a name a shot of camera play keeps as written. */
    private int entityId(Args args, String name) throws CommandException {
        String word = args.word(name);
        if (!word.startsWith("@")) {
            return args.entity(name).id();
        }
        OptionalInt found = names.find(word.substring(1));
        if (found.isEmpty()) {
            throw new CommandException("no_entity", word);
        }
        return found.getAsInt();
    }

    /** A position, which rides along aboard a sub-level, or the point a look at an entity aims at. */
    private CameraControl.Point point(CommandContext context, Args args, String name, Level level)
        throws CommandException {
        if (args.type(name) == ArgType.ENTITY) {
            return aimAt(entity(args, name, level));
        }
        if (args.type(name) == ArgType.SUB_LEVEL) {
            return subLevel(args, name, level);
        }
        Position look = context.resolve(args.position(name));
        return CameraControl.drawn(level, new Vec3(look.x(), look.y(), look.z()));
    }

    /** The origin of the sub-level a {@code sub:<ref>} slot names, where each frame draws it. */
    private static CameraControl.Point subLevel(Args args, String name, Level level) throws CommandException {
        if (SableSupport.subLevels() == SubLevels.NONE) {
            throw new CommandException("unsupported", args.word(name) + " needs Sable");
        }
        return CameraControl.subLevelOrigin(level, SableSupport.resolve(args.word(name).substring("sub:".length())));
    }

    /**
     * A point of a path: around a sub-level, a relative one is an offset in the world's axes from its origin, in each
     * frame; else as {@link #point} has it.
     */
    private static CameraControl.Point pathPoint(CommandContext context, Level level, Position asked,
        CameraControl.Point frame) throws CommandException {
        if (frame != null && asked.relative()) {
            return partialTick -> frame.at(partialTick).add(asked.x(), asked.y(), asked.z());
        }
        Position point = context.resolve(asked);
        return CameraControl.drawn(level, new Vec3(point.x(), point.y(), point.z()));
    }

    private Entity entity(Args args, String name, Level level) throws CommandException {
        Entity entity = level.getEntity(entityId(args, name));
        if (entity == null) {
            throw new CommandException("no_entity", args.word(name));
        }
        return entity;
    }

    /** Half way up the entity where each frame draws it, null once it is gone. */
    private static CameraControl.Point aimAt(Entity entity) {
        // Interpolated like the entity's model, so a camera tracking a walking mob turns smoothly.
        return partialTick -> entity.isRemoved() ? null
            : entity.getPosition(partialTick).add(0, entity.getBbHeight() / 2, 0);
    }

    private static int duration(Args args) throws CommandException {
        int duration = args.duration("duration").ticks();
        if (duration < 1) {
            throw new CommandException("syntax", "over takes 1t or more");
        }
        return duration;
    }

    private static CameraPath.Ease ease(Args args) {
        return args.option("ease") == null ? CameraPath.Ease.LINEAR
            : CameraPath.Ease.valueOf(args.option("ease").toUpperCase(Locale.ROOT));
    }

    /** Where the camera is drawn now: a placed camera, else the player's eyes. */
    private static Vec3 drawnNow() {
        CameraControl.Placement placement = CameraControl.placement();
        Vec3 at = placement == null ? null : placement.position().at(1);
        return at != null ? at : Minecraft.getInstance().player.getEyePosition();
    }

    /** As camera at answers, after its frames, for where the placement then puts the camera. */
    private static Action answer(Level level, CameraControl.Placement placed) {
        return ActionSupport.afterFrames(2, () -> {
            Vec3 at = placed.position().at(1);
            return Result.ok(placedFields(cameraFields(level, at), level, at, placed.target()));
        });
    }

    /**
     * As camera at answers for the point, for a move, whose reply names its end as given, a plot position aboard a
     * sub-level included, not where the frame draws it.
     */
    private static Action answer(Level level, Vec3 point, CameraControl.Placement placed) {
        return ActionSupport.afterFrames(2, () ->
            Result.ok(placedFields(cameraFields(level, point), level, point, placed.target())));
    }

    /** A timed motion placed: it answers at its end, or at once in the background. */
    private static Action start(CameraMotion motion, int duration) {
        CameraControl.placeMove(motion);
        if (motion.background()) {
            return () -> Result.ok(new Fields().put("moving", true, "moving").put("over", duration,
                "over=" + duration + "t"));
        }
        return motion;
    }

    private CameraTimeline.Starter move(CommandContext context, Args args) throws CommandException {
        int duration = duration(args);
        present(args, "target");
        return () -> glide(context, args, duration);
    }

    private Action glide(CommandContext context, Args args, int duration) throws CommandException {
        CameraPath.Ease ease = ease(args);
        Level level = Minecraft.getInstance().level;
        var player = Minecraft.getInstance().player;
        CameraControl.Placement before = CameraControl.placement();
        CameraControl.Point frame = args.has("frame") ? subLevel(args, "frame", level) : null;
        List<CameraControl.Point> points = new ArrayList<>();
        points.add(before != null ? before.position() : player::getEyePosition);
        for (Position via : args.positions("via")) {
            points.add(pathPoint(context, level, via, frame));
        }
        CameraControl.Point end = pathPoint(context, level, args.position("pos"), frame);
        points.add(end);
        CameraMove.Look look = CameraMove.Look.KEEP;
        CameraControl.Point target = null;
        if (args.has("target")) {
            boolean ahead = args.type("target") == ArgType.KEYWORD;
            look = ahead ? CameraMove.Look.AHEAD : CameraMove.Look.TARGET;
            target = ahead ? null : args.type("target") == ArgType.POSITION && frame != null
                ? pathPoint(context, level, args.position("target"), frame) : point(context, args, "target", level);
        }
        // Around a sub-level the end moves with it, so the answer reads where the camera then rests.
        Vec3 endPoint = frame == null ? end.at(1) : null;
        return start(new CameraMove(points, look, target, before, before == null ? player.getViewVector(1) : null,
            duration, ease, args.has("background"), fov(args), roll(args),
            placed -> endPoint == null ? answer(level, placed) : answer(level, endPoint, placed)), duration);
    }

    private CameraTimeline.Starter follow(CommandContext context, Args args) throws CommandException {
        if (args.type("subject") == ArgType.SUB_LEVEL && args.has("turning")) {
            throw new CommandException("syntax", "turning is for an entity: a sub-level is followed in the world's "
                + "axes, and a camera at a plot position turns with it");
        }
        if (args.has("offset")) {
            offset(args);
        }
        present(args, "subject");
        return () -> placeFollow(args);
    }

    private Action placeFollow(Args args) throws CommandException {
        Level level = Minecraft.getInstance().level;
        boolean turning = args.has("turning");
        CameraControl.Placement placement;
        if (args.type("subject") == ArgType.SUB_LEVEL) {
            CameraControl.Point origin = subLevel(args, "subject", level);
            Vec3 delta = drawnNow().subtract(origin.at(1));
            Vec offset = args.has("offset") ? offset(args) : new Vec(delta.x, delta.y, delta.z);
            placement = CameraFollow.placement(origin, offset);
        } else {
            Entity entity = entity(args, "subject", level);
            Vec offset = args.has("offset") ? offset(args) : CameraFollow.offsetOf(entity, drawnNow(), turning);
            placement = CameraFollow.placement(entity, offset, turning, aimAt(entity));
        }
        placement = placement.withLens(CameraControl.lensWith(fov(args), roll(args)));
        CameraControl.place(placement);
        return answer(level, placement);
    }

    /** follow's at: an offset ~dx,dy,dz from what it follows. */
    private static Vec offset(Args args) throws CommandException {
        Position at = args.position("offset");
        if (!at.relative()) {
            throw new CommandException("syntax", "follow at takes an offset ~dx,dy,dz from what it follows, not "
                + args.word("offset"));
        }
        return new Vec(at.x(), at.y(), at.z());
    }

    private CameraTimeline.Starter orbit(CommandContext context, Args args) throws CommandException {
        int duration = duration(args);
        present(args, "centre");
        return () -> circle(context, args, duration);
    }

    private Action circle(CommandContext context, Args args, int duration) throws CommandException {
        Level level = Minecraft.getInstance().level;
        CameraControl.Point centre = point(context, args, "centre", level);
        double degrees = args.has("degrees") ? args.number("degrees", 360) : 360;
        return start(new CameraOrbit(centre, drawnNow(), centre.at(1), degrees, duration, ease(args),
            args.has("background"), fov(args), roll(args), placed -> answer(level, placed)), duration);
    }

    /** camera wait: answers once no camera move is under way, as camera at answers where the camera then rests. */
    private static final class WaitForMove implements Action, Engine.SelfTimed {
        private Action answer;

        @Override
        public Result tick() throws CommandException {
            if (answer == null) {
                if (CameraControl.moving() != null || CameraTimeline.CAMERA.playing()) {
                    return null;
                }
                Result failure = CameraTimeline.CAMERA.takeFailure();
                if (failure != null) {
                    return failure;
                }
                CameraControl.Placement placement = CameraControl.placement();
                Level level = Minecraft.getInstance().level;
                answer = placement == null || level == null ? () -> Result.ok() : answer(level, placement);
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
}

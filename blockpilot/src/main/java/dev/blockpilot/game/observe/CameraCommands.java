package dev.blockpilot.game.observe;

import dev.blockpilot.api.Action;
import dev.blockpilot.api.ArgType;
import dev.blockpilot.api.Args;
import dev.blockpilot.api.CommandContext;
import dev.blockpilot.api.CommandException;
import dev.blockpilot.api.CommandSpec;
import dev.blockpilot.api.Fields;
import dev.blockpilot.api.Position;
import dev.blockpilot.api.Result;
import dev.blockpilot.game.common.Ids;
import dev.blockpilot.game.common.Sides;
import dev.blockpilot.game.sable.Rel;
import dev.blockpilot.game.sable.SableSupport;
import dev.blockpilot.game.sable.SubLevels;
import dev.blockpilot.protocol.CommandRegistry;
import dev.blockpilot.protocol.Engine;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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

    void register(CommandRegistry registry) {
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
        CameraControl.place(new CameraControl.Placement(CameraControl.drawn(level, position), target));
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
        return CameraControl.drawn(level, new Vec3(look.x(), look.y(), look.z()));
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
            points.add(CameraControl.drawn(level, new Vec3(point.x(), point.y(), point.z())));
        }
        Position end = context.resolve(args.position("pos"));
        Vec3 endPoint = new Vec3(end.x(), end.y(), end.z());
        points.add(CameraControl.drawn(level, endPoint));
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

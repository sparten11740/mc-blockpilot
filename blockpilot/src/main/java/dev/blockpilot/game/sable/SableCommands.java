package dev.blockpilot.game.sable;

import dev.blockpilot.api.Action;
import dev.blockpilot.api.ArgType;
import dev.blockpilot.api.Args;
import dev.blockpilot.api.BlockpilotApi;
import dev.blockpilot.api.CommandContext;
import dev.blockpilot.api.CommandException;
import dev.blockpilot.api.CommandSpec;
import dev.blockpilot.api.Fields;
import dev.blockpilot.api.Position;
import dev.blockpilot.api.Result;
import dev.blockpilot.api.Row;
import dev.blockpilot.game.common.BlockMatcher;
import dev.blockpilot.game.common.BlockScan;
import dev.blockpilot.game.common.Ids;
import dev.blockpilot.game.common.Shapes;
import dev.blockpilot.game.common.Sides;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The {@code sable:} commands. They use only {@link SubLevels}, so they live outside the compat package, and
 * {@link SableSupport} registers them when the integration turns on.
 */
final class SableCommands {
    private static final String REGISTRANT = "blockpilot";
    private static final int DEFAULT_RADIUS = 128;
    private static final int TP_REACH = 2;
    private static final int TP_SETTLE_TICKS = 2;
    /** Also long enough for a client that has yet to receive the sub-level after a long teleport. */
    private static final int TP_BOARD_TICKS = 100;
    /** Half a block and a little: a bottom slab drops the feet half a block below the spot's floor. */
    private static final double TP_ON_SPOT = 0.6;
    /** How far above the spot an untracked player is put to fall onto it: as far as the sub-level's step may miss. */
    private static final double TP_DROP = 0.25;
    /**
     * The chunk columns sable:find may look through, half a default Sable plot's width. Plot bounds turned back from a
     * tilted world box take in far more than the sub-level, but the scan skips the empty sections around it.
     */
    private static final long MAX_FIND_COLUMNS = 64L * 64;
    /** Keeps a bound that ends on a block edge from taking in the next block. */
    private static final double EDGE = 1.0E-6;
    private static final String REFERENCE = "a sub-level: its UUID, its name, or here (the one the player stands on "
        + "or rides, else the one under the crosshair)";

    private SableCommands() {
    }

    /** Built in: Blockpilot adds them to its registry itself, never through the API that other mods use. */
    static List<BlockpilotApi.Registration> registrations() {
        List<BlockpilotApi.Registration> registrations = new ArrayList<>();
        registrations.add(builtIn(CommandSpec.query("list", SableCommands::list)
            .needsWorld().optionalArg("radius", ArgType.INT)
            .option("side", "server: the integrated server's sub-levels, also those the client has not received "
                + "(cheats on)", Sides.NAMES)
            .help("the sub-levels within radius (default " + DEFAULT_RADIUS + ") of the player, nearest first")
            .field("server_only", "client list in singleplayer with cheats: sub-levels the server holds within the "
                + "radius that the client does not; left out at 0")
            .field("name", "its name, else the start of its UUID").field("id", "UUID").field("at", "world position")
            .field("dist", "blocks from the player").field("speed", "blocks per second")
            .field("size", "blocks along x, y and z of its plot bounds: the world bounds turned back into the plot, "
                + "so larger while it is turned").example("sable:list 64")));
        registrations.add(builtIn(CommandSpec.query("info", SableCommands::info)
            .needsWorld().arg("sub", ArgType.UUID, ArgType.WORD).optionalKeyword("brief", "brief")
            .option("side", "server: read the integrated server's copy, also of a sub-level the client has not "
                + "received, with agl measured in the server's level (cheats on)", Sides.NAMES)
            .help("one sub-level's pose, velocity, height above ground and bounds; with brief only yaw, pitch, roll, "
                + "speed, climb, y and agl, e.g. to sample a climb; " + REFERENCE)
            .field("name", "its name, else the start of its UUID").field("id", "UUID").field("at", "world position")
            .field("yaw", "degrees turned from how it was built; 90 is a quarter turn west")
            .field("pitch", "degrees, nose down positive").field("roll", "degrees, right side down positive")
            .field("speed", "blocks per second").field("vel", "blocks per second along x, y and z")
            .field("climb", "brief only: vertical speed, blocks per second")
            .field("y", "brief only: world y of its position")
            .field("agl", "height above ground: from the lowest corner of its lowest block, however it is turned, to "
                + "the top of the ground straight under that corner; none above nothing loaded, or when the side "
                + "read holds none of its blocks")
            .field("box", "world bounds")
            .field("plot", "plot bounds: the world bounds turned back into the plot, so larger while it is turned")
            .field("rides", "the player stands on or rides it")
            .example("sable:info here")));
        registrations.add(builtIn(CommandSpec.query("world", SableCommands::world)
            .needsWorld().arg("pos", ArgType.POSITION)
            .help("where a point in a sub-level's plot is in the world now")
            .field("world", "world position").field("sub", "the sub-level whose plot holds the point")
            .example("sable:world 20481032.5,129.5,20481033.5")));
        registrations.add(builtIn(CommandSpec.query("plot", SableCommands::plot)
            .needsWorld().arg("sub", ArgType.UUID, ArgType.WORD).arg("pos", ArgType.POSITION)
            .help("the point in a sub-level's plot that is at a world position now; " + REFERENCE)
            .field("plot", "plot position").field("sub", "the sub-level").example("sable:plot here ~0,1,0")));
        registrations.add(builtIn(CommandSpec.query("find", SableCommands::find)
            .needsWorld().arg("sub", ArgType.UUID, ArgType.WORD)
            .arg("what", ArgType.TAG, ArgType.PATTERN, ArgType.ID, ArgType.BLOCK_STATE)
            .optionalArg("limit", ArgType.INT)
            .option("side", "server: read the integrated server's copy of the plot, also of a sub-level the client "
                + "has not received (cheats on)", Sides.NAMES)
            .option("sort", "along the sub-level's own plot axes, smallest first, or largest first with -",
                List.of("x", "y", "z", "-x", "-y", "-z"))
            .option("near", "a world or plot position to order by distance from, instead of the player",
                ArgType.POSITION)
            .help("blocks aboard a sub-level (id, #tag or *pattern, each also with states: *_slab[type=top]), "
                + "nearest to the player first, or to near=<pos>, or with sort=x|y|z|-x|-y|-z along the sub-level's "
                + "own plot axes (level ones nearest first); the order covers all matches, then the limit (default 5, "
                + "max " + BlockScan.MAX_FIND_LIMIT + ") cuts it, and count= gives all matches when it left some out; "
                + REFERENCE)
            .field("block", "block id").field("at", "plot position").field("world", "world position")
            .field("count", "matches in all, when the limit left some out")
            .example("sable:find here *door* 3")));
        registrations.add(builtIn(CommandSpec.action("tp", SableCommands::teleport)
            .needsServer().form(form -> form.arg("pos", ArgType.POSITION))
            .form(form -> form.arg("sub", ArgType.UUID, ArgType.WORD).arg("what", ArgType.TAG, ArgType.PATTERN,
                ArgType.ID, ArgType.BLOCK_STATE))
            .help("puts the player aboard: on a free spot within " + TP_REACH + " blocks of a block in a sub-level's "
                + "plot, standing on that sub-level (cheats on); the nearest spot from which the block is in sight, "
                + "else the nearest, with hidden. It answers once the client shows the player standing on that spot, "
                + "also on a sub-level under way, else fails after " + TP_BOARD_TICKS / 20 + "s with err not_aboard "
                + "and what the client shows. sable:tp <sub> <block|#tag|*pattern> (also with states) boards next to "
                + "the matching block aboard nearest the player, read from the server's copy, so also a sub-level the "
                + "client hasn't received; " + REFERENCE)
            .field("at", "where the player stands, world position").field("plot", "that spot's plot position")
            .field("sub", "the sub-level").field("dist", "blocks from the player's eyes to the block's centre")
            .field("hidden", "no free spot within reach has the block in sight")
            .field("block", "with <sub> <what>: the block boarded next to").field("target", "its plot position")
            .example("sable:tp 20481032,129,20481033")));
        return registrations;
    }

    private static BlockpilotApi.Registration builtIn(CommandSpec spec) {
        return new BlockpilotApi.Registration(SableSupport.NAMESPACE, REGISTRANT, spec);
    }

    private static Result list(CommandContext context, Args args) throws CommandException {
        int radius = Math.max(1, args.integer("radius", DEFAULT_RADIUS));
        // Read here: with side=server the listing runs on the server thread.
        Vec3 from = Minecraft.getInstance().player.position();
        Result listed = Sides.read(args, (level, server) -> Result.ok().withRows(near(level, from, radius).stream()
            .map(info -> listRow(info, from)).toList()));
        if ("server".equals(args.option("side")) || !listed.isOk()) {
            return listed;
        }
        // A client that missed a sub-level (a lost packet, a world joined mid-assembly) lists nothing for it.
        Set<String> known = listed.rows().stream().map(Row::key).collect(Collectors.toSet());
        Long serverOnly = Sides.tryServer(level -> near(level, from, radius).stream()
            .filter(info -> !known.contains(info.id().toString())).count());
        return serverOnly == null || serverOnly == 0 ? listed
            : Result.ok(new Fields().put("server_only", serverOnly)).withRows(listed.rows());
    }

    private static List<SubLevels.Info> near(Level level, Vec3 from, int radius) {
        return SableSupport.subLevels().near(level, new AABB(from, from).inflate(radius)).stream()
            .filter(info -> info.position().distanceTo(from) <= radius)
            .sorted(Comparator.comparingDouble(info -> info.position().distanceTo(from))).toList();
    }

    private static Row listRow(SubLevels.Info info, Vec3 from) {
        return Row.of(info.id().toString(), new Fields().bare("name", info.name())
            .bare("id", info.id().toString()).bare("at", Rel.of(info.position()))
            .hidden("at_abs", Rel.absolute(info.position()))
            .put("dist", tenths(info.position().distanceTo(from))).put("speed", tenths(info.velocity().length()))
            .put("size", size(info.plotBounds()), "size=" + String.join("x", size(info.plotBounds()).stream()
                .map(String::valueOf).toList())));
    }

    private static Result info(CommandContext context, Args args) throws CommandException {
        UUID known = knownSubLevel(args, "sub");
        UUID playerId = Minecraft.getInstance().player.getUUID();
        return Sides.read(args, (level, server) -> {
            SubLevels.Info info = resolve(level, known, args, "sub");
            Vec3 velocity = info.velocity();
            Player player = level.getPlayerByUUID(playerId);
            SubLevels.Info carrying = player == null ? null : SableSupport.subLevels().carrying(player);
            boolean rides = carrying != null && carrying.id().equals(info.id());
            Object agl = heightAboveGround(level, info);
            if (args.has("brief")) {
                return Result.ok(new Fields().tag("yaw", tenths(info.yaw())).tag("pitch", tenths(info.pitch()))
                    .tag("roll", tenths(info.roll())).put("speed", tenths(velocity.length()))
                    .put("climb", tenths(velocity.y)).put("y", tenths(info.position().y)).put("agl", agl));
            }
            return Result.ok(new Fields().bare("name", info.name()).bare("id", info.id().toString())
                .bare("at", Rel.of(info.position())).hidden("at_abs", Rel.absolute(info.position()))
                .tag("yaw", tenths(info.yaw())).tag("pitch", tenths(info.pitch())).tag("roll", tenths(info.roll()))
                .put("speed", tenths(velocity.length()))
                .put("vel", Position.absolute(tenths(velocity.x), tenths(velocity.y), tenths(velocity.z)))
                .put("agl", agl).put("box", bounds(info.worldBounds()), "box=" + boundsText(info.worldBounds()))
                .put("plot", bounds(info.plotBounds()), "plot=" + boundsText(info.plotBounds()))
                .put("rides", rides));
        });
    }

    private static Result world(CommandContext context, Args args) throws CommandException {
        Level level = Minecraft.getInstance().level;
        Position point = context.resolve(args.position("pos"));
        Vec3 plot = new Vec3(point.x(), point.y(), point.z());
        SubLevels.Info info = SableSupport.subLevels().at(level, BlockPos.containing(plot));
        if (info == null) {
            throw new CommandException("no_sublevel", point + " is in no sub-level's plot");
        }
        return Result.ok(new Fields().bare("world", Rel.absolute(SableSupport.subLevels().toWorld(level, plot)))
            .put("sub", info.name()));
    }

    private static Result plot(CommandContext context, Args args) throws CommandException {
        SubLevels.Info info = resolve(args, "sub");
        Position point = context.resolve(args.position("pos"));
        Vec3 plot = SableSupport.subLevels().toPlot(Minecraft.getInstance().level, info.id(),
            new Vec3(point.x(), point.y(), point.z()));
        if (plot == null) {
            throw new CommandException("no_sublevel", info.id().toString());
        }
        return Result.ok(new Fields().bare("plot", Rel.absolute(plot)).put("sub", info.name()));
    }

    private static Result find(CommandContext context, Args args) throws CommandException {
        UUID known = knownSubLevel(args, "sub");
        BlockMatcher matcher = BlockMatcher.parse(args.word("what"));
        int limit = Mth.clamp(args.integer("limit", 5), 1, BlockScan.MAX_FIND_LIMIT);
        Comparator<Aboard> sort = args.option("sort") == null ? null : alongAxis(args.option("sort"));
        Position near = args.option("near") == null ? null : context.resolve(Position.parse(args.option("near")));
        Vec3 eyes = Minecraft.getInstance().player.getEyePosition();
        return Sides.read(args, (level, server) -> {
            SubLevels.Info info = resolve(level, known, args, "sub");
            // A plot position becomes its world point; distances agree, as a sub-level only moves and turns.
            Vec3 from = near == null ? eyes : SableSupport.subLevels().toWorld(level, point(near));
            List<Aboard> found = aboard(level, info, matcher, from);
            if (sort != null) {
                // A stable sort, so blocks level on the axis keep their distance order.
                found.sort(sort);
            }
            List<Row> rows = found.stream().limit(limit)
                .map(block -> Row.of(block.pos().toShortString(), new Fields()
                    .bare("block", Ids.of(level.getBlockState(block.pos())))
                    .bare("at", Rel.absolute(block.pos())).put("world", Rel.absolute(block.world()))))
                .toList();
            return (found.size() > limit ? Result.ok(new Fields().put("count", found.size())) : Result.ok())
                .withRows(rows);
        });
    }

    /** Whole coordinates name a block and mean its centre, where the blocks found are measured from. */
    private static Vec3 point(Position position) {
        Vec3 point = new Vec3(position.x(), position.y(), position.z());
        boolean whole = point.x == Math.floor(point.x) && point.y == Math.floor(point.y)
            && point.z == Math.floor(point.z);
        return whole ? Vec3.atCenterOf(BlockPos.containing(point)) : point;
    }

    /** {@code x}, {@code y} or {@code z} of the plot position, smallest first; with {@code -} largest first. */
    private static Comparator<Aboard> alongAxis(String sort) {
        boolean descending = sort.startsWith("-");
        String axis = sort.substring(descending ? 1 : 0);
        Comparator<Aboard> order = Comparator.comparingInt(block -> switch (axis) {
            case "x" -> block.pos().getX();
            case "y" -> block.pos().getY();
            default -> block.pos().getZ();
        });
        return descending ? order.reversed() : order;
    }

    private record Aboard(BlockPos pos, Vec3 world) {
    }

    /** The blocks aboard the sub-level that match, nearest to the world point first, as the level holds them. */
    private static List<Aboard> aboard(Level level, SubLevels.Info info, BlockMatcher matcher, Vec3 from)
        throws CommandException {
        AABB plot = info.plotBounds();
        BlockPos min = BlockPos.containing(plot.minX, plot.minY, plot.minZ);
        BlockPos max = BlockPos.containing(plot.maxX - EDGE, plot.maxY - EDGE, plot.maxZ - EDGE);
        long columns = (long) ((max.getX() >> 4) - (min.getX() >> 4) + 1) * ((max.getZ() >> 4) - (min.getZ() >> 4) + 1);
        if (columns > MAX_FIND_COLUMNS) {
            throw new CommandException("too_large", columns + " chunk columns in the plot bounds, max "
                + MAX_FIND_COLUMNS);
        }
        SubLevels subLevels = SableSupport.subLevels();
        List<Aboard> found = new ArrayList<>();
        BlockScan.box(level, min, max, matcher, false,
            (pos, state) -> found.add(new Aboard(pos.immutable(), subLevels.toWorld(level, Vec3.atCenterOf(pos)))));
        found.sort(Comparator.comparingDouble(block -> block.world().distanceTo(from)));
        return found;
    }

    private static Action teleport(CommandContext context, Args args) throws CommandException {
        UUID playerId = Minecraft.getInstance().player.getUUID();
        Landing[] landing = {null};
        Result boarded;
        // The server step runs now; the client sees the player's new place a tick or two later.
        if (args.has("pos")) {
            Position target = context.resolve(args.position("pos"));
            BlockPos block = BlockPos.containing(target.x(), target.y(), target.z());
            boarded = Sides.onServer((level, server) -> {
                landing[0] = board(level, playerId, block);
                return Result.ok();
            });
        } else {
            UUID known = knownSubLevel(args, "sub");
            BlockMatcher matcher = BlockMatcher.parse(args.word("what"));
            Vec3 eyes = Minecraft.getInstance().player.getEyePosition();
            boarded = Sides.onServer((level, server) -> {
                SubLevels.Info info = resolve(level, known, args, "sub");
                List<Aboard> found = aboard(level, info, matcher, eyes);
                if (found.isEmpty()) {
                    throw new CommandException("no_block", args.word("what") + " aboard " + info.name());
                }
                BlockPos block = found.get(0).pos();
                landing[0] = board(level, playerId, block);
                landing[0].fields().bare("block", Ids.of(level.getBlockState(block)))
                    .put("target", Rel.absolute(block));
                return Result.ok();
            });
        }
        if (!boarded.isOk()) {
            return () -> boarded;
        }
        int[] ticks = {0};
        return () -> {
            String offSpot = land(landing[0]);
            if (++ticks[0] > TP_SETTLE_TICKS && offSpot == null) {
                return Result.ok(new Fields().bare("at", Rel.absolute(Minecraft.getInstance().player.position())))
                    .with(landing[0].fields());
            }
            if (ticks[0] >= TP_BOARD_TICKS) {
                throw new CommandException("not_aboard", offSpot);
            }
            return null;
        };
    }

    /**
     * On the client thread: null once the client shows the player carried by the sub-level with the feet on the spot.
     * Else what it shows instead, and the player is put on the spot as the client has the sub-level, if it has it.
     * The server put them there as it has the sub-level, which on the client lags a tick or so behind, and while
     * Sable carries the player the client's place in the plot is the one that holds.
     */
    private static String land(Landing landing) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        SubLevels subLevels = SableSupport.subLevels();
        Vec3 plot = subLevels.toPlot(minecraft.level, landing.sub(), player.position());
        if (plot == null) {
            return "the client has no copy of " + landing.name();
        }
        SubLevels.Info carrier = subLevels.carrying(player);
        boolean carried = carrier != null && carrier.id().equals(landing.sub());
        Vec3 floor = Vec3.atBottomCenterOf(landing.spot());
        if (carried && plot.distanceTo(floor) <= TP_ON_SPOT) {
            return null;
        }
        Vec3 spot = subLevels.toWorld(minecraft.level, floor);
        if (carried) {
            // Sable carries a player it tracks from where the sub-level is now to where it is next tick.
            player.setPos(spot);
            player.setDeltaMovement(Vec3.ZERO);
        } else {
            // Early in the next tick Sable moves the sub-level, then the player moves, and Sable starts carrying them
            // when that move lands on it: put them just above where the spot will be, falling.
            Vec3 next = spot.add(subLevels.velocity(minecraft.level, floor).scale(1 / 20.0));
            player.setPos(next.add(0, TP_DROP, 0));
            player.setDeltaMovement(new Vec3(0, -2 * TP_DROP, 0));
        }
        player.resetFallDistance();
        if (player.getAbilities().flying) {
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
        }
        return "the client shows the player at " + Rel.absolute(plot) + " in " + landing.name() + "'s plot, "
            + (carrier == null ? "on no sub-level" : "aboard " + carrier.name());
    }

    /**
     * The UUID of a sub-level reference the client can resolve, else null when it names by UUID or name one the client
     * has not received, for the server to look up. {@code here} needs the client's crosshair, so it must resolve here.
     */
    private static UUID knownSubLevel(Args args, String name) throws CommandException {
        try {
            return resolve(args, name).id();
        } catch (CommandException e) {
            if (!e.code().equals("no_sublevel") || args.word(name).equals("here")) {
                throw e;
            }
            return null;
        }
    }

    /** On either thread: the sub-level {@link #knownSubLevel} returned as that level holds it, else looked up there. */
    private static SubLevels.Info resolve(Level level, UUID known, Args args, String name) throws CommandException {
        SubLevels.Info info = known != null ? SableSupport.subLevels().info(level, known) : resolve(level, args, name);
        if (info == null) {
            throw new CommandException("no_sublevel", args.word(name));
        }
        return info;
    }

    /**
     * Where {@code sable:tp} put the player: the spot in the sub-level's plot, and the reply's fields after {@code at}.
     */
    private record Landing(BlockPos spot, UUID sub, String name, Fields fields) {
    }

    /** On the server thread: puts the player on the best free spot next to the block aboard, see {@link #freeSpot}. */
    private static Landing board(Level serverLevel, UUID playerId, BlockPos block) throws CommandException {
        ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(playerId);
        if (player == null) {
            throw new CommandException("not_in_world");
        }
        //? if >=26.1 {
        /*ServerLevel level = player.level();
        *///?} else {
        ServerLevel level = player.serverLevel();
        //?}
        SubLevels subLevels = SableSupport.subLevels();
        SubLevels.Info info = subLevels.at(level, block);
        if (info == null) {
            throw new CommandException("no_sublevel", Rel.absolute(block) + " is in no sub-level's plot");
        }
        double eyeHeight = player.getEyeHeight(Pose.STANDING);
        Spot spot = freeSpot(level, block, subLevels, eyeHeight);
        if (spot == null) {
            throw new CommandException("no_room", "no free spot within " + TP_REACH + " blocks of "
                + Rel.absolute(block));
        }
        Vec3 at = subLevels.toWorld(level, Vec3.atBottomCenterOf(spot.pos()));
        //? if >=26.1 {
        /*player.teleportTo(level, at.x, at.y, at.z, Set.of(), player.getYRot(), player.getXRot(), false);
        *///?} else {
        player.teleportTo(level, at.x, at.y, at.z, player.getYRot(), player.getXRot());
        //?}
        // A sub-level moves as one piece, so the distance in its plot is the distance in the world.
        double dist = eyes(spot.pos(), eyeHeight).distanceTo(Vec3.atCenterOf(block));
        Fields fields = new Fields().put("plot", Rel.absolute(spot.pos())).put("sub", info.name())
            .put("dist", tenths(dist));
        if (!spot.seen()) {
            fields.put("hidden", true, "hidden");
        }
        return new Landing(spot.pos(), info.id(), info.name(), fields);
    }

    private record Spot(BlockPos pos, boolean seen) {
    }

    /**
     * The nearest spot next to the block with room for the player, standing on a block of the same sub-level, from
     * whose eyes the block is in sight; else the nearest spot, not seen. Never the block's own cell, which a block
     * without collision (a lever, a pressure plate) leaves free. Sight stops at any outline shape, glass too: the line
     * a click needs.
     */
    private static Spot freeSpot(Level level, BlockPos target, SubLevels subLevels, double eyeHeight) {
        List<BlockPos> spots = new ArrayList<>();
        BlockPos.betweenClosed(target.offset(-TP_REACH, -TP_REACH, -TP_REACH), target.offset(TP_REACH, TP_REACH,
            TP_REACH)).forEach(pos -> spots.add(pos.immutable()));
        spots.sort(Comparator.comparingDouble(pos -> pos.distSqr(target)));
        BlockPos nearest = null;
        for (BlockPos spot : spots) {
            if (!spot.equals(target) && passable(level, spot) && passable(level, spot.above())
                && !passable(level, spot.below()) && subLevels.at(level, spot.below()) != null) {
                if (sees(level, eyes(spot, eyeHeight), target)) {
                    return new Spot(spot, true);
                }
                if (nearest == null) {
                    nearest = spot;
                }
            }
        }
        return nearest == null ? null : new Spot(nearest, false);
    }

    private static Vec3 eyes(BlockPos spot, double eyeHeight) {
        return Vec3.atBottomCenterOf(spot).add(0, eyeHeight, 0);
    }

    /**
     * Whether the first outline shape on the line from the eyes to the centre of the block's shape is the block's own.
     * The centre of the shape, not of the block: a lever's shape ends below the block's centre.
     */
    private static boolean sees(Level level, Vec3 eyes, BlockPos target) {
        BlockHitResult hit = level.clip(new ClipContext(eyes, Shapes.centre(level, target), ClipContext.Block.OUTLINE,
            ClipContext.Fluid.NONE, CollisionContext.empty()));
        return hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(target);
    }

    private static boolean passable(Level level, BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    /** The sub-level an argument names: {@code here}, a UUID or a name; fails when it names none, or a name several. */
    static SubLevels.Info resolve(Args args, String name) throws CommandException {
        return resolve(Minecraft.getInstance().level, args, name);
    }

    /** {@link #resolve(Args, String)} against that level; {@code here} reads the client, so only on its thread. */
    private static SubLevels.Info resolve(Level level, Args args, String name) throws CommandException {
        Minecraft minecraft = Minecraft.getInstance();
        SubLevels subLevels = SableSupport.subLevels();
        String word = args.word(name);
        if (args.type(name) == ArgType.UUID) {
            SubLevels.Info info = subLevels.info(level, args.uuid(name));
            if (info == null) {
                throw new CommandException("no_sublevel", word);
            }
            return info;
        }
        if (word.equals("here")) {
            return here(minecraft, subLevels);
        }
        List<SubLevels.Info> named = subLevels.all(level).stream()
            .filter(info -> info.name().equals(word)).toList();
        if (named.isEmpty()) {
            throw new CommandException("no_sublevel", word);
        }
        if (named.size() > 1) {
            throw new CommandException("ambiguous", word + " is " + String.join(" ", named.stream()
                .map(info -> info.id().toString()).toList()));
        }
        return named.get(0);
    }

    private static SubLevels.Info here(Minecraft minecraft, SubLevels subLevels) throws CommandException {
        SubLevels.Info info = subLevels.carrying(minecraft.player);
        if (info == null && minecraft.hitResult instanceof BlockHitResult hit
            && hit.getType() == HitResult.Type.BLOCK) {
            info = subLevels.at(minecraft.level, hit.getBlockPos());
        }
        if (info == null) {
            throw new CommandException("no_sublevel", "here: the player is on none and the crosshair is on none");
        }
        return info;
    }

    /**
     * Blocks from the sub-level's lowest point down to the top of the first block with a collision shape straight under
     * it, or {@code none} over unloaded chunks or the void, or when the level holds none of its blocks. Sub-levels keep
     * their blocks in their plots, so only the world below counts.
     */
    private static Object heightAboveGround(Level level, SubLevels.Info info) {
        Vec3 lowest = LowestPoint.of(level, info);
        if (lowest == null) {
            return "none";
        }
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(Mth.floor(lowest.x),
            Math.min(Mth.floor(lowest.y), level.getMaxBuildHeight() - 1), Mth.floor(lowest.z));
        if (!Sides.loaded(level, cursor)) {
            return "none";
        }
        for (; cursor.getY() >= level.getMinBuildHeight(); cursor.move(0, -1, 0)) {
            VoxelShape shape = level.getBlockState(cursor).getCollisionShape(level, cursor);
            if (!shape.isEmpty()) {
                return tenths(Math.max(0, lowest.y - (cursor.getY() + shape.max(Direction.Axis.Y))));
            }
        }
        return "none";
    }

    private static double tenths(double value) {
        return Math.round(value * 10) / 10.0 + 0.0;
    }

    private static List<Long> size(AABB bounds) {
        return List.of(Math.round(bounds.getXsize()), Math.round(bounds.getYsize()), Math.round(bounds.getZsize()));
    }

    private static Map<String, Position> bounds(AABB box) {
        Map<String, Position> bounds = new LinkedHashMap<>();
        bounds.put("min", Rel.absolute(new Vec3(box.minX, box.minY, box.minZ)));
        bounds.put("max", Rel.absolute(new Vec3(box.maxX, box.maxY, box.maxZ)));
        return bounds;
    }

    private static String boundsText(AABB box) {
        Map<String, Position> bounds = bounds(box);
        return bounds.get("min") + ".." + bounds.get("max");
    }
}

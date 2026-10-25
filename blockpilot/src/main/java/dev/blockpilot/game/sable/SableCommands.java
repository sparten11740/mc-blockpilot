package dev.blockpilot.game.sable;

import dev.blockpilot.api.Action;
import dev.blockpilot.api.Args;
import dev.blockpilot.api.ArgType;
import dev.blockpilot.api.BlockpilotApi;
import dev.blockpilot.api.CommandContext;
import dev.blockpilot.api.CommandException;
import dev.blockpilot.api.CommandSpec;
import dev.blockpilot.api.Fields;
import dev.blockpilot.api.Position;
import dev.blockpilot.api.Result;
import dev.blockpilot.api.Row;
import dev.blockpilot.game.BlockMatcher;
import dev.blockpilot.game.BlockScan;
import dev.blockpilot.game.Ids;
import dev.blockpilot.game.Rel;
import dev.blockpilot.game.Sides;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
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
    private static final int MAX_FIND_LIMIT = 100;
    /** The positions sable:find may scan: as many as find's widest cube, 128 blocks each way. */
    private static final long MAX_FIND_BLOCKS = 257L * 257 * 257;
    /** Keeps a bound that ends on a block edge from taking in the next block. */
    private static final double EDGE = 1.0E-6;
    /** Sub-levels are seen inside the world border, so this box holds them all. */
    private static final AABB EVERYWHERE = new AABB(-3.0E7, -3.0E7, -3.0E7, 3.0E7, 3.0E7, 3.0E7);
    private static final String REFERENCE = "a sub-level: its UUID, its name, or here (the one the player stands on "
        + "or rides, else the one under the crosshair)";

    private SableCommands() {
    }

    static void register() {
        BlockpilotApi.register(SableSupport.NAMESPACE, REGISTRANT, CommandSpec.query("list", SableCommands::list)
            .needsWorld().optionalArg("radius", ArgType.INT)
            .option("side", "server: the integrated server's sub-levels, also those the client has not received "
                + "(cheats on)", Sides.NAMES)
            .help("the sub-levels within radius (default " + DEFAULT_RADIUS + ") of the player, nearest first")
            .field("server_only", "client list in singleplayer with cheats: sub-levels the server holds within the "
                + "radius that the client does not; left out at 0")
            .field("name", "its name, else the start of its UUID").field("id", "UUID").field("at", "world position")
            .field("dist", "blocks from the player").field("speed", "blocks per second")
            .field("size", "blocks along x, y and z of its plot bounds").example("sable:list 64"));
        BlockpilotApi.register(SableSupport.NAMESPACE, REGISTRANT, CommandSpec.query("info", SableCommands::info)
            .needsWorld().arg("sub", ArgType.UUID, ArgType.WORD).optionalKeyword("brief", "brief")
            .help("one sub-level's pose, velocity, height above ground and bounds; with brief only yaw, pitch, roll, "
                + "speed, climb and agl, e.g. to sample a climb; " + REFERENCE)
            .field("name", "its name, else the start of its UUID").field("id", "UUID").field("at", "world position")
            .field("yaw", "degrees turned from how it was built; 90 is a quarter turn west")
            .field("pitch", "degrees, nose down positive").field("roll", "degrees, right side down positive")
            .field("speed", "blocks per second").field("vel", "blocks per second along x, y and z")
            .field("climb", "brief only: vertical speed, blocks per second")
            .field("agl", "height above ground: from the bottom of its world bounds to the top of the ground under "
                + "their centre; none above nothing loaded")
            .field("box", "world bounds").field("plot", "plot bounds, looser while it is turned")
            .field("rides", "the player stands on or rides it")
            .example("sable:info here"));
        BlockpilotApi.register(SableSupport.NAMESPACE, REGISTRANT, CommandSpec.query("world", SableCommands::world)
            .needsWorld().arg("pos", ArgType.POSITION)
            .help("where a point in a sub-level's plot is in the world now")
            .field("world", "world position").field("sub", "the sub-level whose plot holds the point")
            .example("sable:world 20481032.5,129.5,20481033.5"));
        BlockpilotApi.register(SableSupport.NAMESPACE, REGISTRANT, CommandSpec.query("plot", SableCommands::plot)
            .needsWorld().arg("sub", ArgType.UUID, ArgType.WORD).arg("pos", ArgType.POSITION)
            .help("the point in a sub-level's plot that is at a world position now; " + REFERENCE)
            .field("plot", "plot position").field("sub", "the sub-level").example("sable:plot here ~0,1,0"));
        BlockpilotApi.register(SableSupport.NAMESPACE, REGISTRANT, CommandSpec.query("find", SableCommands::find)
            .needsWorld().arg("sub", ArgType.UUID, ArgType.WORD).arg("what", ArgType.TAG, ArgType.PATTERN, ArgType.ID)
            .optionalArg("limit", ArgType.INT)
            .option("side", "server: read the integrated server's copy of the plot (cheats on)", Sides.NAMES)
            .help("blocks aboard a sub-level (id, #tag or *pattern), nearest to the player first (limit default 5, "
                + "max 100); " + REFERENCE)
            .field("block", "block id").field("at", "plot position").field("world", "world position")
            .example("sable:find here *door* 3"));
        BlockpilotApi.register(SableSupport.NAMESPACE, REGISTRANT, CommandSpec.action("tp", SableCommands::teleport)
            .onServer().arg("pos", ArgType.POSITION)
            .help("puts the player aboard: on a free spot within " + TP_REACH + " blocks of a block in a sub-level's "
                + "plot, standing on that sub-level (cheats on)")
            .field("at", "where the player stands, world position").field("plot", "that spot's plot position")
            .field("sub", "the sub-level").example("sable:tp 20481032,129,20481033"));
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
        Minecraft minecraft = Minecraft.getInstance();
        SubLevels.Info info = resolve(args, "sub");
        Vec3 velocity = info.velocity();
        SubLevels.Info carrying = SableSupport.subLevels().carrying(minecraft.player);
        boolean rides = carrying != null && carrying.id().equals(info.id());
        Object agl = heightAboveGround(minecraft.level, info.worldBounds());
        if (args.has("brief")) {
            return Result.ok(new Fields().tag("yaw", tenths(info.yaw())).tag("pitch", tenths(info.pitch()))
                .tag("roll", tenths(info.roll())).put("speed", tenths(velocity.length()))
                .put("climb", tenths(velocity.y)).put("agl", agl));
        }
        return Result.ok(new Fields().bare("name", info.name()).bare("id", info.id().toString())
            .bare("at", Rel.of(info.position())).hidden("at_abs", Rel.absolute(info.position()))
            .tag("yaw", tenths(info.yaw())).tag("pitch", tenths(info.pitch())).tag("roll", tenths(info.roll()))
            .put("speed", tenths(velocity.length()))
            .put("vel", Position.absolute(tenths(velocity.x), tenths(velocity.y), tenths(velocity.z)))
            .put("agl", agl).put("box", bounds(info.worldBounds()), "box=" + boundsText(info.worldBounds()))
            .put("plot", bounds(info.plotBounds()), "plot=" + boundsText(info.plotBounds()))
            .put("rides", rides));
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
        SubLevels.Info info = resolve(args, "sub");
        BlockMatcher matcher = BlockMatcher.parse(args.word("what"));
        int limit = Mth.clamp(args.integer("limit", 5), 1, MAX_FIND_LIMIT);
        AABB plot = info.plotBounds();
        BlockPos min = BlockPos.containing(plot.minX, plot.minY, plot.minZ);
        BlockPos max = BlockPos.containing(plot.maxX - EDGE, plot.maxY - EDGE, plot.maxZ - EDGE);
        long volume = (long) (max.getX() - min.getX() + 1) * (max.getY() - min.getY() + 1)
            * (max.getZ() - min.getZ() + 1);
        if (volume > MAX_FIND_BLOCKS) {
            throw new CommandException("too_large", volume + " blocks in the plot bounds, max " + MAX_FIND_BLOCKS);
        }
        Vec3 eyes = Minecraft.getInstance().player.getEyePosition();
        return Sides.read(args, (level, server) -> {
            SubLevels subLevels = SableSupport.subLevels();
            Map<BlockPos, Vec3> found = new LinkedHashMap<>();
            BlockScan.box(level, min, max, matcher, false,
                (pos, state) -> found.put(pos.immutable(), subLevels.toWorld(level, Vec3.atCenterOf(pos))));
            return Result.ok().withRows(found.entrySet().stream()
                .sorted(Comparator.comparingDouble(entry -> entry.getValue().distanceTo(eyes))).limit(limit)
                .map(entry -> Row.of(entry.getKey().toShortString(), new Fields()
                    .bare("block", Ids.of(level.getBlockState(entry.getKey())))
                    .bare("at", Rel.absolute(entry.getKey())).put("world", Rel.absolute(entry.getValue()))))
                .toList());
        });
    }

    private static Action teleport(CommandContext context, Args args) throws CommandException {
        Position target = context.resolve(args.position("pos"));
        BlockPos block = BlockPos.containing(target.x(), target.y(), target.z());
        Minecraft minecraft = Minecraft.getInstance();
        MinecraftServer server = minecraft.getSingleplayerServer();
        UUID playerId = minecraft.player.getUUID();
        int[] ticks = {0};
        Result[] done = {null};
        return () -> {
            if (ticks[0]++ > 0) {
                // The client sees the player's new place a tick or two later.
                return ticks[0] > TP_SETTLE_TICKS ? done[0] : null;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
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
                throw new CommandException("no_sublevel", target + " is in no sub-level's plot");
            }
            BlockPos spot = freeSpot(level, block, subLevels);
            if (spot == null) {
                throw new CommandException("no_room", "no free spot within " + TP_REACH + " blocks of " + target);
            }
            Vec3 at = subLevels.toWorld(level, Vec3.atBottomCenterOf(spot));
            //? if >=26.1 {
            /*player.teleportTo(level, at.x, at.y, at.z, Set.of(), player.getYRot(), player.getXRot(), false);
            *///?} else {
            player.teleportTo(level, at.x, at.y, at.z, player.getYRot(), player.getXRot());
            //?}
            done[0] = Result.ok(new Fields().bare("at", Rel.absolute(at)).put("plot", Rel.absolute(spot))
                .put("sub", info.name()));
            return null;
        };
    }

    /** The nearest spot with room for the player, standing on a block of the same sub-level. */
    private static BlockPos freeSpot(Level level, BlockPos target, SubLevels subLevels) {
        List<BlockPos> spots = new ArrayList<>();
        BlockPos.betweenClosed(target.offset(-TP_REACH, -TP_REACH, -TP_REACH), target.offset(TP_REACH, TP_REACH,
            TP_REACH)).forEach(pos -> spots.add(pos.immutable()));
        spots.sort(Comparator.comparingDouble(pos -> pos.distSqr(target)));
        for (BlockPos spot : spots) {
            if (passable(level, spot) && passable(level, spot.above()) && !passable(level, spot.below())
                && subLevels.at(level, spot.below()) != null) {
                return spot;
            }
        }
        return null;
    }

    private static boolean passable(Level level, BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    /** The sub-level an argument names: {@code here}, a UUID or a name; fails when it names none, or a name several. */
    static SubLevels.Info resolve(Args args, String name) throws CommandException {
        Minecraft minecraft = Minecraft.getInstance();
        Level level = minecraft.level;
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
        List<SubLevels.Info> named = subLevels.near(level, EVERYWHERE).stream()
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
     * Blocks from the bottom of the bounds down to the top of the first block with a collision shape under their
     * centre, or {@code none} over unloaded chunks or the void. Sub-levels keep their blocks in their plots, so only
     * the world below counts.
     */
    private static Object heightAboveGround(Level level, AABB bounds) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(Mth.floor(bounds.getCenter().x),
            Math.min(Mth.floor(bounds.minY), level.getMaxBuildHeight() - 1), Mth.floor(bounds.getCenter().z));
        if (!Sides.loaded(level, cursor)) {
            return "none";
        }
        for (; cursor.getY() >= level.getMinBuildHeight(); cursor.move(0, -1, 0)) {
            VoxelShape shape = level.getBlockState(cursor).getCollisionShape(level, cursor);
            if (!shape.isEmpty()) {
                return tenths(Math.max(0, bounds.minY - (cursor.getY() + shape.max(Direction.Axis.Y))));
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

package dev.blockpilot.game.sable;

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
import dev.blockpilot.game.Rel;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The {@code sable:} commands. They use only {@link SubLevels}, so they live outside the compat package, and
 * {@link SableSupport} registers them when the integration turns on.
 */
final class SableCommands {
    private static final String REGISTRANT = "blockpilot";
    private static final int DEFAULT_RADIUS = 128;
    /** Sub-levels are seen inside the world border, so this box holds them all. */
    private static final AABB EVERYWHERE = new AABB(-3.0E7, -3.0E7, -3.0E7, 3.0E7, 3.0E7, 3.0E7);
    private static final String REFERENCE = "a sub-level: its UUID, its name, or here (the one the player stands on "
        + "or rides, else the one under the crosshair)";

    private SableCommands() {
    }

    static void register() {
        BlockpilotApi.register(SableSupport.NAMESPACE, REGISTRANT, CommandSpec.query("list", SableCommands::list)
            .needsWorld().optionalArg("radius", ArgType.INT)
            .help("the sub-levels within radius (default " + DEFAULT_RADIUS + ") of the player, nearest first")
            .field("name", "its name, else the start of its UUID").field("id", "UUID").field("at", "world position")
            .field("dist", "blocks from the player").field("speed", "blocks per second")
            .field("size", "blocks along x, y and z of its plot bounds").example("sable:list 64"));
        BlockpilotApi.register(SableSupport.NAMESPACE, REGISTRANT, CommandSpec.query("info", SableCommands::info)
            .needsWorld().arg("sub", ArgType.UUID, ArgType.WORD)
            .help("one sub-level's pose, velocity and bounds; " + REFERENCE)
            .field("name", "its name, else the start of its UUID").field("id", "UUID").field("at", "world position")
            .field("yaw", "degrees turned from how it was built; 90 is a quarter turn west")
            .field("pitch", "degrees, nose down positive").field("roll", "degrees, right side down positive")
            .field("speed", "blocks per second").field("vel", "blocks per second along x, y and z")
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
    }

    private static Result list(CommandContext context, Args args) {
        Minecraft minecraft = Minecraft.getInstance();
        int radius = Math.max(1, args.integer("radius", DEFAULT_RADIUS));
        Vec3 from = minecraft.player.position();
        List<SubLevels.Info> near = SableSupport.subLevels().near(minecraft.level, new AABB(from, from).inflate(radius))
            .stream().filter(info -> info.position().distanceTo(from) <= radius)
            .sorted(Comparator.comparingDouble(info -> info.position().distanceTo(from))).toList();
        List<Row> rows = near.stream().map(info -> Row.of(info.id().toString(), new Fields().bare("name", info.name())
            .bare("id", info.id().toString()).bare("at", Rel.of(info.position()))
            .hidden("at_abs", Rel.absolute(info.position()))
            .put("dist", tenths(info.position().distanceTo(from))).put("speed", tenths(info.velocity().length()))
            .put("size", size(info.plotBounds()), "size=" + String.join("x", size(info.plotBounds()).stream()
                .map(String::valueOf).toList())))).toList();
        return Result.ok().withRows(rows);
    }

    private static Result info(CommandContext context, Args args) throws CommandException {
        Minecraft minecraft = Minecraft.getInstance();
        SubLevels.Info info = resolve(args, "sub");
        Vec3 velocity = info.velocity();
        boolean rides = info.id().equals(SableSupport.subLevels().carrying(minecraft.player));
        return Result.ok(new Fields().bare("name", info.name()).bare("id", info.id().toString())
            .bare("at", Rel.of(info.position())).hidden("at_abs", Rel.absolute(info.position()))
            .tag("yaw", tenths(info.yaw())).tag("pitch", tenths(info.pitch())).tag("roll", tenths(info.roll()))
            .put("speed", tenths(velocity.length()))
            .put("vel", Position.absolute(tenths(velocity.x), tenths(velocity.y), tenths(velocity.z)))
            .put("box", bounds(info.worldBounds()), "box=" + boundsText(info.worldBounds()))
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
        UUID carrying = subLevels.carrying(minecraft.player);
        SubLevels.Info info = carrying == null ? null : subLevels.info(minecraft.level, carrying);
        if (info == null && minecraft.hitResult instanceof BlockHitResult hit
            && hit.getType() == HitResult.Type.BLOCK) {
            info = subLevels.at(minecraft.level, hit.getBlockPos());
        }
        if (info == null) {
            throw new CommandException("no_sublevel", "here: the player is on none and the crosshair is on none");
        }
        return info;
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

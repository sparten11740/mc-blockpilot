package dev.blockpilot.game;

import static dev.blockpilot.game.PlayerActions.PLOT_AIM_HELP;
import static dev.blockpilot.game.PlayerActions.WITHIN_BLOCK;
import static dev.blockpilot.game.PlayerActions.WITHIN_BLOCK_HELP;
import static dev.blockpilot.game.PlayerActions.closeScreen;
import static dev.blockpilot.game.PlayerActions.hit;
import static dev.blockpilot.game.PlayerActions.lookAt;
import static dev.blockpilot.game.PlayerActions.minecraft;
import static dev.blockpilot.game.PlayerActions.named;
import static dev.blockpilot.game.PlayerActions.options;
import static dev.blockpilot.game.PlayerActions.player;
import static dev.blockpilot.game.PlayerActions.point;

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
import dev.blockpilot.game.common.Ids;
import dev.blockpilot.game.input.InputState;
import dev.blockpilot.game.path.Pathfinders;
import dev.blockpilot.game.path.PathSearch;
import dev.blockpilot.game.path.Terrain;
import dev.blockpilot.game.path.WorldTerrain;
import dev.blockpilot.game.sable.Rel;
import dev.blockpilot.game.sable.SableSupport;
import dev.blockpilot.protocol.CommandRegistry;
import java.util.function.Supplier;
import java.util.stream.Stream;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Commands that move the player or turn its camera, by the same key mappings and look a player has. */
final class MovementCommands {
    /** A move by distance fails when the player hasn't got this far from where it last progressed in that long. */
    private static final double MOVE_PROGRESS = 0.1;
    private static final int MOVE_STUCK_TICKS = 20;
    /** Set by {@code fly on}, cleared by {@code fly off}; see {@link #holdFlight(boolean)}. */
    private static boolean holdFlight;

    void register(CommandRegistry registry) {
        registry.register(CommandSpec.action("face", this::face).needsWorld().group("movement")
            .form(form -> form.arg("target", ArgType.POSITION, ArgType.ENTITY).optional(WITHIN_BLOCK))
            .help("turns to look at a point (decimals allowed, block centers for whole numbers) or an entity; "
                + "returns what the crosshair then hits, and when that is nothing, how far the target is and how far "
                + "the player reaches, to tell a target out of reach from empty air; turn sets yaw and pitch instead. "
                + PLOT_AIM_HELP + WITHIN_BLOCK_HELP)
            .field("dist", "with aim:nothing: blocks from the eyes to the point aimed at")
            .field("reach", "with aim:nothing: the player's reach for that kind of target (block or entity), in blocks")
            .example("face 12.5,70.2,-3.4"));
        registry.register(CommandSpec.action("turn", this::turn).needsWorld().group("movement")
            .arg("yaw", ArgType.NUMBER).arg("pitch", ArgType.NUMBER).optionalArg("ticks", ArgType.DURATION)
            .help("sets the camera to yaw/pitch (degrees), optionally smoothed over a duration")
            .example("turn 90 20 10t"));
        registry.register(CommandSpec.action("move", this::move).needsWorld().group("movement")
            .keyword("dir", "forward", "back", "left", "right").arg("amount", ArgType.NUMBER, ArgType.DURATION)
            .help("holds a movement key for <blocks> or <Nt> ticks; by blocks it fails with err stuck after 20 ticks "
                + "without progress").field("moved", "blocks moved").example("move forward 3"));
        registry.register(CommandSpec.action("jump", (context, args) -> pulse(context, options().keyJump, 2, "jump"))
            .needsWorld().group("movement").help("one jump").example("jump"));
        registry.register(CommandSpec.action("sneak", (context, args) -> args.has("state")
                ? toggle(context, options().keyShift, args.word("state"))
                : pulse(context, options().keyShift, 5, "sneak"))
            .needsWorld().group("movement").optionalKeyword("state", "on", "off")
            .help("holds or releases sneak; alone, taps it for a fifth of a second, as to leave a seat or vehicle; "
                + InputCommands.HELD_UNTIL)
            .example("sneak on"));
        registry.register(CommandSpec.action("sprint", (context, args) -> toggle(context, options().keySprint,
            args.word("state"))).needsWorld().group("movement").keyword("state", "on", "off")
            .help("holds or releases sprint; " + InputCommands.HELD_UNTIL).example("sprint on"));
        registry.register(CommandSpec.action("fly", this::fly).needsWorld().group("movement")
            .keyword("state", "on", "off")
            .help("starts or stops flying where the game mode allows it (creative, spectator); fly on holds through "
                + "landings and teleports until fly off").example("fly on"));
        registry.register(CommandSpec.action("goto", this::navigate).needsWorld().group("movement")
            .arg("target", ArgType.POSITION, ArgType.ENTITY)
            .option("near", "stop within this many blocks", ArgType.NUMBER)
            .help("walks there through loaded chunks (walk, step up 1, drop up to 3, swim, ladders; never digs). To a "
                + "position without near=, fails at once with err no_path and why when the player can't stand in "
                + "that cell (a block fills it, no room for the head, nothing to stand on, danger, an unloaded chunk). "
                + "With Baritone installed, it walks through Baritone instead, which digs and places blocks on the way "
                + "unless Baritone's allowBreak and allowPlace settings are off")
            .field("took", "seconds").field("by", "baritone when Baritone walked").example("goto 120,64,-30"));
    }

    /**
     * Called every client tick: after {@code fly on}, flight that a landing or a teleport ended (vanilla stops
     * flying on touching the ground) is turned back on, until {@code fly off}, leaving the world, a game mode that
     * can't fly, or the agent losing control (disconnected or toggled off), so a human is never put back in the air.
     */
    static void holdFlight(boolean agentInControl) {
        LocalPlayer player = player();
        if (!holdFlight) {
            return;
        }
        if (!agentInControl || player == null || !player.getAbilities().mayfly) {
            holdFlight = false;
        } else if (!player.getAbilities().flying) {
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
        }
    }

    private Action face(CommandContext context, Args args) throws CommandException {
        closeScreen();
        Supplier<Vec3> target = point(context, args, "target");
        boolean entity = args.type("target") == ArgType.ENTITY;
        int[] step = {0};
        return named("face", () -> {
            Vec3 point = target.get();
            if (point == null) {
                throw new CommandException("no_entity", args.word("target"));
            }
            lookAt(point);
            if (step[0]++ == 0) {
                return null;
            }
            Fields fields = hit();
            if ("nothing".equals(fields.get("aim"))) {
                double reach = entity ? PlayerActions.entityReach() : PlayerActions.blockReach();
                fields.put("dist", Numbers.round(player().getEyePosition().distanceTo(point)))
                    .put("reach", Numbers.round(reach));
            }
            return Result.ok(fields);
        });
    }

    private Action turn(CommandContext context, Args args) {
        float yaw = (float) args.number("yaw", 0);
        float pitch = Mth.clamp((float) args.number("pitch", 0), -90, 90);
        Duration duration = args.duration("ticks");
        int ticks = duration == null ? 1 : Math.max(1, duration.ticks());
        LocalPlayer player = player();
        float startYaw = player.getYRot();
        float deltaYaw = Mth.wrapDegrees(yaw - startYaw);
        float startPitch = player.getXRot();
        int[] step = {0};
        return named("turn", () -> {
            step[0]++;
            float progress = Math.min(1f, step[0] / (float) ticks);
            player.setYRot(startYaw + deltaYaw * progress);
            player.setYHeadRot(player.getYRot());
            player.setXRot(startPitch + (pitch - startPitch) * progress);
            return step[0] >= ticks ? Result.ok() : null;
        });
    }

    private Action move(CommandContext context, Args args) throws CommandException {
        closeScreen();
        KeyMapping key = switch (args.word("dir")) {
            case "forward" -> options().keyUp;
            case "back" -> options().keyDown;
            case "left" -> options().keyLeft;
            default -> options().keyRight;
        };
        Duration duration = args.type("amount") == ArgType.DURATION ? args.duration("amount") : null;
        double blocks = duration == null ? args.number("amount", 0) : 0;
        int session = context.sessionId();
        Vec3 start = player().position();
        int[] ticks = {0};
        Vec3[] progress = {start};
        int[] sinceProgress = {0};
        return new Action() {
            @Override
            public Result tick() throws CommandException {
                InputState.hold(session, key);
                ticks[0]++;
                Vec3 position = player().position();
                double distance = Math.hypot(position.x - start.x, position.z - start.z);
                double moved = Math.round(distance * 10) / 10.0;
                if (Math.hypot(position.x - progress[0].x, position.z - progress[0].z) >= MOVE_PROGRESS) {
                    progress[0] = position;
                    sinceProgress[0] = 0;
                } else if (duration == null && ++sinceProgress[0] >= MOVE_STUCK_TICKS) {
                    InputState.release(key);
                    throw new CommandException("stuck", null, new Fields().put("moved", moved, "moved=" + moved));
                }
                boolean done = duration != null ? ticks[0] >= duration.ticks() : distance >= blocks;
                if (!done) {
                    return null;
                }
                InputState.release(key);
                return Result.ok(new Fields().put("moved", moved, "moved " + moved));
            }

            @Override
            public void cancel() {
                InputState.release(key);
            }

            @Override
            public String label() {
                return "move";
            }
        };
    }

    /** Holds a key mapping for a few ticks, then lets go. */
    private static Action pulse(CommandContext context, KeyMapping key, int ticks, String label) {
        int session = context.sessionId();
        int[] step = {0};
        return new Action() {
            @Override
            public Result tick() {
                if (step[0]++ == 0) {
                    InputState.hold(session, key);
                    return null;
                }
                if (step[0] < ticks) {
                    return null;
                }
                InputState.release(key);
                return Result.ok();
            }

            @Override
            public void cancel() {
                InputState.release(key);
            }

            @Override
            public String label() {
                return label;
            }
        };
    }

    private static Action toggle(CommandContext context, KeyMapping key, String state) {
        if (state.equals("on")) {
            InputState.hold(context.sessionId(), key);
        } else {
            InputState.release(key);
        }
        return () -> Result.ok();
    }

    private Action fly(CommandContext context, Args args) throws CommandException {
        LocalPlayer player = player();
        if (!player.getAbilities().mayfly) {
            throw new CommandException("syntax", "flying needs creative or spectator mode");
        }
        holdFlight = args.word("state").equals("on");
        player.getAbilities().flying = holdFlight;
        player.onUpdateAbilities();
        return () -> Result.ok(new Fields().put("flying", player.getAbilities().flying,
            player.getAbilities().flying ? "flying" : "not flying"));
    }

    private Action navigate(CommandContext context, Args args) throws CommandException {
        closeScreen();
        boolean entityTarget = args.type("target") == ArgType.ENTITY;
        double near = args.numberOption("near", entityTarget ? 2 : 0);
        Supplier<Vec3> target;
        if (entityTarget) {
            Entity entity = minecraft().level.getEntity(args.entity("target").id());
            if (entity == null) {
                throw new CommandException("no_entity", args.word("target"));
            }
            target = () -> entity.isRemoved() ? null : entity.position();
        } else {
            Position absolute = context.resolve(args.position("target"));
            BlockPos block = BlockPos.containing(absolute.x(), absolute.y(), absolute.z());
            if (SableSupport.subLevels().at(minecraft().level, block) != null) {
                throw new CommandException("syntax", "goto can't walk onto a sub-level; use sable:tp " + absolute);
            }
            if (!args.hasOption("near")) {
                String blocked = unstandable(minecraft().level, block);
                if (blocked != null) {
                    throw new CommandException("no_path", blocked);
                }
            }
            Vec3 goal = Vec3.atBottomCenterOf(block);
            target = () -> goal;
        }
        return Pathfinders.installed().current().walk(context.sessionId(), Pathfinders.positions(target), near);
    }

    /** Why the player can't stand in the cell, naming the blocks, or null when it can. */
    private static String unstandable(Level level, BlockPos cell) {
        Terrain terrain = new WorldTerrain(level);
        PathSearch.Blocker blocker = PathSearch.blocker(terrain, cell.getX(), cell.getY(), cell.getZ());
        if (blocker == null) {
            return null;
        }
        return switch (blocker) {
            case UNLOADED -> Rel.of(cell) + " is in a chunk that isn't loaded";
            case FILLED -> Ids.of(level.getBlockState(cell)) + " fills " + Rel.of(cell)
                + (PathSearch.blocker(terrain, cell.getX(), cell.getY() + 1, cell.getZ()) == null
                    ? "; stand at " + Rel.of(cell.above()) : "");
            case NO_HEADROOM -> Ids.of(level.getBlockState(cell.above())) + " at " + Rel.of(cell.above())
                + " leaves no room for the head";
            case DANGEROUS -> {
                BlockPos danger = Stream.of(cell, cell.above(), cell.below())
                    .filter(pos -> terrain.dangerous(pos.getX(), pos.getY(), pos.getZ())).findFirst().orElse(cell);
                yield Ids.of(level.getBlockState(danger)) + " at " + Rel.of(danger) + " is dangerous";
            }
            case NO_FLOOR -> "nothing to stand on at " + Rel.of(cell);
        };
    }
}

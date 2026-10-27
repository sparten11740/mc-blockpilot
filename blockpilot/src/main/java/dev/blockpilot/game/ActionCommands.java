package dev.blockpilot.game;

import dev.blockpilot.api.Action;
import dev.blockpilot.api.ArgType;
import dev.blockpilot.api.Args;
import dev.blockpilot.api.CommandContext;
import dev.blockpilot.api.CommandException;
import dev.blockpilot.api.CommandSpec;
import dev.blockpilot.api.Duration;
import dev.blockpilot.api.EntityRef;
import dev.blockpilot.api.Fields;
import dev.blockpilot.api.Form;
import dev.blockpilot.api.Position;
import dev.blockpilot.api.Result;
import dev.blockpilot.game.input.AimedClick;
import dev.blockpilot.game.input.InputState;
import dev.blockpilot.game.path.Navigator;
import dev.blockpilot.game.sable.SableSupport;
import dev.blockpilot.game.sable.SubLevels;
import dev.blockpilot.mixin.KeyMappingAccessor;
import dev.blockpilot.protocol.CommandRegistry;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Commands that act as the player: they turn the camera, hold movement keys and click use/attack through the
 * game's own key mappings, so everything reaches the server and other mods as ordinary player input.
 */
final class ActionCommands {
    private static final int MINE_SEARCH_RADIUS = 32;
    /** A move by distance fails when the player hasn't got this far from where it last progressed in that long. */
    private static final double MOVE_PROGRESS = 0.1;
    private static final int MOVE_STUCK_TICKS = 20;
    /** {@code at <u,v,w>}: a point inside the target block, in the block's own frame. */
    private static final Consumer<Form> WITHIN_BLOCK = at -> at.keyword("at", "at").arg("point", ArgType.POSITION);
    private static final String WITHIN_BLOCK_HELP = "at u,v,w aims at that point of the block, each 0 to 1 in the "
        + "frame its model is drawn in facing north (u west to east, v up, w north to south), turned with the block's "
        + "facing; status shows hit=u,v,w for the point under the crosshair";
    /** How long {@code use} re-aims at its target for the crosshair to rest on it before it gives up. */
    private static final int MAX_AIM_TICKS = 10;
    /** Set by {@code fly on}, cleared by {@code fly off}; see {@link #holdFlight(boolean)}. */
    private static boolean holdFlight;

    void register(CommandRegistry registry) {
        registry.register(CommandSpec.action("face", this::face).needsWorld().group("movement")
            .form(form -> form.arg("target", ArgType.POSITION, ArgType.ENTITY).optional(WITHIN_BLOCK))
            .help("turns to look at a point (decimals allowed, block centers for whole numbers) or an entity; "
                + "returns what the crosshair then hits. " + WITHIN_BLOCK_HELP).example("face 12.5,70.2,-3.4"));
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
        registry.register(CommandSpec.action("sneak", (context, args) -> toggle(context, options().keyShift,
            args.word("state"))).needsWorld().group("movement").keyword("state", "on", "off")
            .help("holds or releases sneak").example("sneak on"));
        registry.register(CommandSpec.action("sprint", (context, args) -> toggle(context, options().keySprint,
            args.word("state"))).needsWorld().group("movement").keyword("state", "on", "off")
            .help("holds or releases sprint").example("sprint on"));
        registry.register(CommandSpec.action("fly", this::fly).needsWorld().group("movement")
            .keyword("state", "on", "off")
            .help("starts or stops flying where the game mode allows it (creative, spectator); fly on holds through "
                + "landings and teleports until fly off").example("fly on"));
        registry.register(CommandSpec.action("goto", this::navigate).needsWorld().group("movement")
            .arg("target", ArgType.POSITION, ArgType.ENTITY)
            .option("near", "stop within this many blocks", ArgType.NUMBER)
            .help("walks there through loaded chunks (walk, step up 1, drop up to 3, swim, ladders; never digs)")
            .field("took", "seconds").example("goto 120,64,-30"));
        registry.register(CommandSpec.action("use", this::use).needsWorld().group("interact")
            .form(form -> form.optional(target -> target.arg("target", ArgType.POSITION, ArgType.ENTITY)
                .optional(WITHIN_BLOCK)).optionalKeyword("any", "any").optionalKeyword("sneak", "sneak"))
            .option("hold", "keep the use key down for a duration (eat, draw a bow, charge)",
                ArgType.DURATION, ArgType.SECONDS)
            .help("right-clicks: aims first if given a target, else whatever the crosshair hits; returns the hit. "
                + "At a block (not air) or an entity it clicks only once the crosshair rests on it, and after half a "
                + "second fails with err obstructed naming what is in the way, or err out_of_reach; any clicks "
                + "whatever the crosshair ends on (a multi-block panel whose mod takes the click on any of its "
                + "blocks), adding off_target when that was not the target. The server gets the game's own crosshair "
                + "hit: where the aimed look ray first meets a block's shape, which the reply reports as aim and hit=. "
                + "Sneaking with an item in either hand, the game skips the block's own use and uses the item, which "
                + "the reply names as sneak_item. " + WITHIN_BLOCK_HELP)
            .field("aim", "what was clicked")
            .field("off_target", "with any: the target, when the click went to something else")
            .field("sneak_item", "with sneak: the item in hand that took the click instead of the block")
            .example("use ~1,0,0 sneak"));
        registry.register(CommandSpec.action("attack", this::attack).needsWorld().group("interact")
            .optionalArg("target", ArgType.ENTITY, ArgType.ID).optionalArg("count", ArgType.COUNT)
            .help("left-clicks the crosshair, or fights e<id> / the nearest of an entity type until it dies, "
                + "at full attack cooldown").example("attack e812"));
        registry.register(CommandSpec.action("mine", this::mine).needsWorld().group("interact")
            .optionalArg("target", ArgType.POSITION, ArgType.TAG, ArgType.PATTERN, ArgType.ID)
            .optionalArg("count", ArgType.COUNT)
            .help("digs the block at <pos> (in reach), what the crosshair hits (no target), or walks to and digs "
                + "the n nearest <block|#tag>; picks the best tool").example("mine #logs x6"));
        registry.register(CommandSpec.action("place", this::place).needsWorld().group("interact")
            .arg("item", ArgType.ID).arg("pos", ArgType.POSITION)
            .help("places the item at pos against an adjacent block").example("place torch ~1,0,0"));
        registry.register(CommandSpec.action("select", this::select).needsWorld().group("inventory")
            .arg("what", ArgType.INT, ArgType.ID)
            .help("selects hotbar slot 0-8, or puts the item into the selected slot")
            .example("select iron_pickaxe"));
        registry.register(CommandSpec.action("swap", this::swap).needsWorld().group("inventory")
            .form(form -> form.arg("a", InventoryOps.SLOT_NAMES, ArgType.INT)
                .arg("b", InventoryOps.SLOT_NAMES, ArgType.INT))
            .help("swaps two inventory slots (0-35, head, chest, legs, feet, offhand)").example("swap 0 9"));
        registry.register(CommandSpec.action("drop", this::drop).needsWorld().group("inventory")
            .form(form -> form.arg("what", InventoryOps.SLOT_NAMES, ArgType.INT, ArgType.ID)
                .optionalArg("count", ArgType.INT))
            .help("drops an item or a slot's stack (all by default)").example("drop dirt 16"));
        registry.register(CommandSpec.action("offhand", this::offhand).needsWorld().group("inventory")
            .arg("item", ArgType.ID).help("moves the item to the offhand").example("offhand shield"));
        registry.register(CommandSpec.action("equip", this::equip).needsWorld().group("inventory")
            .arg("item", ArgType.ID).help("puts on an armor item").example("equip iron_helmet"));
        registry.register(CommandSpec.action("chat", this::chat).needsWorld().group("talk").arg("text", ArgType.TEXT)
            .help("sends a chat message").example("chat hello"));
        registry.register(CommandSpec.action("cmd", this::command).needsWorld().group("talk")
            .arg("command", ArgType.TEXT)
            .help("sends a /command as the player; feedback arrives as ! chat events (see server for captured output)")
            .example("cmd time set day"));
    }

    static Minecraft minecraft() {
        return Minecraft.getInstance();
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

    static net.minecraft.client.Options options() {
        return minecraft().options;
    }

    static LocalPlayer player() {
        return minecraft().player;
    }

    /**
     * Closes an open screen (container, inventory, menu) the way Esc would, so world actions and inventory clicks
     * reach the world and the player's own inventory instead of a screen left open by an earlier command.
     */
    static void closeScreen() {
        Minecraft minecraft = minecraft();
        if (minecraft.screen == null || minecraft.player == null) {
            return;
        }
        if (minecraft.player.containerMenu != minecraft.player.inventoryMenu) {
            minecraft.player.closeContainer();
        } else {
            minecraft.setScreen(null);
        }
    }

    /** Queues one click of the mapping, as a key press would; the game handles it on its next tick. */
    static void click(KeyMapping mapping) {
        KeyMappingAccessor accessor = (KeyMappingAccessor) mapping;
        accessor.blockpilot$setClickCount(accessor.blockpilot$clickCount() + 1);
    }

    /** What the crosshair hits now, as {@code status} reports it. */
    static Fields hit() {
        Fields fields = new Fields();
        PerceptionCommands.aim(minecraft().hitResult, fields);
        if (fields.isEmpty()) {
            fields.put("aim", "nothing", "aim:nothing");
        }
        return fields;
    }

    /** The main hand's item, else the off hand's, or null with both hands empty. */
    private static String heldItem() {
        ItemStack main = player().getMainHandItem();
        ItemStack off = player().getOffhandItem();
        return !main.isEmpty() ? Ids.of(main) : !off.isEmpty() ? Ids.of(off) : null;
    }

    /**
     * The point a position or entity argument names: an entity's centre, with {@code at <u,v,w>} that point of the
     * block, for whole coordinates the centre of the block's shape (a lever's base, not the air above it), else the
     * exact point. An entity's centre follows it, and a point in a sub-level's plot follows the sub-level.
     */
    static Supplier<Vec3> point(CommandContext context, Args args, String name) throws CommandException {
        if (args.type(name) == ArgType.ENTITY) {
            if (args.has("point")) {
                throw new CommandException("syntax", "at takes a block position, not an entity");
            }
            Entity entity = minecraft().level.getEntity(args.entity(name).id());
            if (entity == null) {
                throw new CommandException("no_entity", args.word(name));
            }
            return () -> entity.isRemoved() ? null : entity.getBoundingBox().getCenter();
        }
        Level level = minecraft().level;
        return seen(level, target(level, context.resolve(args.position(name)), args));
    }

    /**
     * Where to aim at a point: the point itself, or for a point in a sub-level's plot where the player will see it at
     * the start of the next tick, following the sub-level.
     */
    private static Supplier<Vec3> seen(Level level, Vec3 point) {
        SubLevels.Info subLevel = SableSupport.subLevels().at(level, BlockPos.containing(point));
        if (subLevel != null) {
            return () -> SableSupport.subLevels().toWorld(level, point).add(lead(level, point, subLevel.id()));
        }
        return () -> point;
    }

    /**
     * With {@code at <u,v,w>} that point of the block in its own frame; for whole coordinates the centre of the block's
     * shape (a lever's base, not the air above it); else the point itself.
     */
    private static Vec3 target(Level level, Position absolute, Args args) throws CommandException {
        if (args.has("point")) {
            BlockPos block = BlockPos.containing(absolute.x(), absolute.y(), absolute.z());
            Position point = args.position("point");
            if (point.relative() || outsideBlock(point.x()) || outsideBlock(point.y()) || outsideBlock(point.z())) {
                throw new CommandException("syntax", "at takes u,v,w each from 0 to 1, not " + args.word("point"));
            }
            BlockFrame.Point offset = PerceptionCommands.frame(level, block)
                .toBlock(new BlockFrame.Point(point.x(), point.y(), point.z()));
            return new Vec3(block.getX() + offset.x(), block.getY() + offset.y(), block.getZ() + offset.z());
        }
        boolean whole = absolute.x() == Math.floor(absolute.x()) && absolute.y() == Math.floor(absolute.y())
            && absolute.z() == Math.floor(absolute.z());
        return whole ? Shapes.centre(level, BlockPos.containing(absolute.x(), absolute.y(), absolute.z()))
            : new Vec3(absolute.x(), absolute.y(), absolute.z());
    }

    /**
     * How far a point aboard moves, as the player sees it, until the crosshair is picked at the start of the next tick:
     * nothing when the player stands on or rides the same sub-level, which carries them both.
     */
    private static Vec3 lead(Level level, Vec3 point, UUID subLevel) {
        SubLevels subLevels = SableSupport.subLevels();
        SubLevels.Info carrier = subLevels.carrying(player());
        if (carrier != null && carrier.id().equals(subLevel)) {
            return Vec3.ZERO;
        }
        Vec3 velocity = subLevels.velocity(level, point);
        return (carrier == null ? velocity : velocity.subtract(carrier.velocity())).scale(1 / 20.0);
    }

    private static boolean outsideBlock(double coordinate) {
        return coordinate < 0 || coordinate > 1;
    }

    /**
     * A target {@code use} clicks only while the crosshair rests on it: the test of a hit, where to aim, and its name
     * as a reply prints it then.
     */
    private record Checked(Predicate<HitResult> on, Supplier<Vec3> aim, Supplier<Object> name) {
    }

    /**
     * The block or entity {@code use}'s target names, to check the crosshair against; null without a target, and for a
     * block without a shape (air), where the position only sets the aim. The aim has no lead: the click turns the
     * player just before the game picks the hit, when the target is where the pick sees it.
     */
    private static Checked checked(CommandContext context, Args args) throws CommandException {
        if (!args.has("target")) {
            return null;
        }
        Level level = minecraft().level;
        if (args.type("target") == ArgType.ENTITY) {
            int id = args.entity("target").id();
            Entity entity = level.getEntity(id);
            if (entity == null) {
                throw new CommandException("no_entity", args.word("target"));
            }
            return new Checked(hit -> hit instanceof EntityHitResult on && hit.getType() == HitResult.Type.ENTITY
                && on.getEntity().getId() == id, () -> entity.isRemoved() ? null : entity.getBoundingBox().getCenter(),
                () -> "e" + id);
        }
        Position absolute = context.resolve(args.position("target"));
        BlockPos block = BlockPos.containing(absolute.x(), absolute.y(), absolute.z());
        if (level.getBlockState(block).getShape(level, block).isEmpty()) {
            return null;
        }
        Vec3 point = target(level, absolute, args);
        Supplier<Vec3> aim = SableSupport.subLevels().at(level, block) == null ? () -> point
            : () -> SableSupport.subLevels().toWorld(level, point);
        return new Checked(hit -> hit instanceof BlockHitResult on && hit.getType() == HitResult.Type.BLOCK
            && on.getBlockPos().equals(block), aim, () -> Rel.of(block));
    }

    /** Why {@code use} gave up on its target: what the crosshair rests on instead, or that it is out of reach. */
    private static CommandException obstruction(Checked target) {
        HitResult hit = minecraft().hitResult;
        if (hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK && !target.on().test(hit)) {
            BlockPos pos = block.getBlockPos();
            return new CommandException("obstructed", Ids.of(minecraft().level.getBlockState(pos)) + " " + Rel.of(pos));
        }
        if (hit instanceof EntityHitResult entity && hit.getType() == HitResult.Type.ENTITY && !target.on().test(hit)) {
            return new CommandException("obstructed", Ids.of(entity.getEntity()) + " e" + entity.getEntity().getId());
        }
        return new CommandException("out_of_reach", String.valueOf(target.name().get()));
    }

    static void lookAt(Vec3 point) {
        player().lookAt(EntityAnchorArgument.Anchor.EYES, point);
    }

    private Action face(CommandContext context, Args args) throws CommandException {
        closeScreen();
        Supplier<Vec3> target = point(context, args, "target");
        int[] step = {0};
        return named("face", () -> {
            Vec3 point = target.get();
            if (point == null) {
                throw new CommandException("no_entity", args.word("target"));
            }
            lookAt(point);
            return step[0]++ == 0 ? null : Result.ok(hit());
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
            Vec3 goal = Vec3.atBottomCenterOf(block);
            target = () -> goal;
        }
        return new Navigator(context.sessionId(), target, near);
    }

    private Action use(CommandContext context, Args args) throws CommandException {
        closeScreen();
        Supplier<Vec3> target = args.has("target") ? point(context, args, "target") : null;
        Checked aimedAt = checked(context, args);
        Checked checked = args.has("any") ? null : aimedAt;
        boolean sneak = args.has("sneak");
        Duration hold = args.durationOption("hold");
        int session = context.sessionId();
        KeyMapping use = options().keyUse;
        KeyMapping shift = options().keyShift;
        String[] sneakItem = {null};
        Runnable press = () -> {
            if (sneak) {
                // Read before the press: using the item may use it up.
                sneakItem[0] = heldItem();
            }
            if (hold != null) {
                InputState.hold(session, use);
            } else {
                click(use);
            }
        };
        return new Action() {
            private int step;
            private int held;
            private int aimed;
            private AimedClick click;
            private Fields hit;

            @Override
            public Result tick() throws CommandException {
                if (step == 0) {
                    step++;
                    if (target != null && checked == null) {
                        Vec3 point = target.get();
                        if (point == null) {
                            throw new CommandException("no_entity", args.word("target"));
                        }
                        lookAt(point);
                    }
                    if (sneak) {
                        InputState.hold(session, shift);
                    }
                    // A checked target is aimed at by the click itself; only the sneak key needs a tick to register.
                    if (sneak || target != null && checked == null) {
                        return null;
                    }
                }
                if (step == 1 && checked != null) {
                    return pressChecked();
                }
                if (step == 1) {
                    step++;
                    press.run();
                    return null;
                }
                if (hit == null) {
                    // The game handled the click during this tick, on the hit still in place now; read at the
                    // press it could be a tick older, which shows on a moving target.
                    hit = clicked();
                }
                if (hold != null && ++held < hold.ticks()) {
                    return null;
                }
                release();
                return Result.ok(hit);
            }

            /**
             * The target may move or be covered, and the game picks the crosshair's hit a tick after the aim: press
             * only on a tick whose hit is the target, re-aiming until then.
             */
            private Result pressChecked() throws CommandException {
                if (click != null && click.fired()) {
                    // Pressed during this tick's key handling, on the hit still in place now.
                    hit = clicked();
                    step++;
                    return null;
                }
                if (click != null && ++aimed >= MAX_AIM_TICKS) {
                    release();
                    throw obstruction(checked);
                }
                if (checked.aim().get() == null) {
                    release();
                    throw new CommandException("no_entity", args.word("target"));
                }
                click = AimedClick.arm(checked.on(), checked.aim(), press);
                return null;
            }

            /** The hit the click went to, and whether that was off the target or went to the held item. */
            private Fields clicked() {
                Fields fields = hit();
                if (aimedAt != null && !aimedAt.on().test(minecraft().hitResult)) {
                    fields.put("off_target", aimedAt.name().get());
                }
                if (sneakItem[0] != null) {
                    fields.put("sneak_item", sneakItem[0]);
                }
                return fields;
            }

            private void release() {
                if (click != null) {
                    click.disarm();
                }
                InputState.release(use);
                if (sneak) {
                    InputState.release(shift);
                }
            }

            @Override
            public void cancel() {
                release();
            }

            @Override
            public String label() {
                return "use";
            }
        };
    }

    private Action attack(CommandContext context, Args args) throws CommandException {
        closeScreen();
        if (!args.has("target")) {
            int[] step = {0};
            Fields[] hit = {null};
            return named("attack", () -> {
                if (step[0]++ == 0) {
                    hit[0] = hit();
                    click(options().keyAttack);
                    return null;
                }
                return Result.ok(hit[0]);
            });
        }
        String token = args.word("target");
        int wanted = args.count("count", 1);
        Supplier<Entity> next;
        if (args.type("target") == ArgType.ENTITY) {
            EntityRef ref = args.entity("target");
            next = () -> minecraft().level.getEntity(ref.id());
        } else {
            var type = BuiltInRegistries.ENTITY_TYPE.getOptional(Ids.parse(token))
                .orElseThrow(() -> new CommandException("no_entity", "no entity type " + token));
            next = () -> minecraft().level.getEntities(player(), player().getBoundingBox().inflate(16),
                    entity -> entity.getType() == type && entity.isAlive()).stream()
                .min((a, b) -> Double.compare(a.distanceToSqr(player()), b.distanceToSqr(player()))).orElse(null);
        }
        int session = context.sessionId();
        return new Action() {
            private Entity current;
            private int defeated;

            @Override
            public Result tick() throws CommandException {
                if (current == null || !current.isAlive() || current.isRemoved()) {
                    if (current != null) {
                        defeated++;
                        if (defeated >= wanted) {
                            InputState.release(options().keyUp);
                            return Result.ok(new Fields().put("killed", defeated, "killed " + defeated));
                        }
                    }
                    current = next.get();
                    if (current == null || !current.isAlive()) {
                        InputState.release(options().keyUp);
                        if (defeated > 0) {
                            return Result.ok(new Fields().put("killed", defeated, "killed " + defeated));
                        }
                        throw new CommandException("no_entity", token);
                    }
                }
                if (current.distanceTo(player()) > 16) {
                    InputState.release(options().keyUp);
                    throw new CommandException("out_of_reach", "e" + current.getId() + " fled");
                }
                lookAt(current.getBoundingBox().getCenter());
                boolean inReach = player().distanceTo(current) < player().entityInteractionRange() - 0.3;
                if (inReach) {
                    InputState.release(options().keyUp);
                } else {
                    InputState.hold(session, options().keyUp);
                }
                HitResult hit = minecraft().hitResult;
                boolean aimed = hit instanceof EntityHitResult entityHit && entityHit.getEntity() == current;
                if (aimed && player().getAttackStrengthScale(0.5f) >= 1f) {
                    click(options().keyAttack);
                }
                return null;
            }

            @Override
            public void cancel() {
                InputState.release(options().keyUp);
            }

            @Override
            public String label() {
                return "attack";
            }
        };
    }

    private Action mine(CommandContext context, Args args) throws CommandException {
        closeScreen();
        int wanted = args.count("count", 1);
        String token = args.word("target");
        int session = context.sessionId();
        if (token == null) {
            HitResult hit = minecraft().hitResult;
            if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
                throw new CommandException("no_block", "the crosshair hits no block");
            }
            return new Dig(session, () -> blockHit.getBlockPos(), 1, false, null);
        }
        if (args.type("target") == ArgType.POSITION) {
            BlockPos pos = Rel.block(context.resolve(args.position("target")));
            Vec3 center = seen(minecraft().level, Vec3.atCenterOf(pos)).get();
            if (center.distanceTo(player().getEyePosition()) > player().blockInteractionRange()) {
                throw new CommandException("out_of_reach", Rel.of(pos).toString());
            }
            return new Dig(session, () -> pos, 1, false, null);
        }
        BlockMatcher matcher = BlockMatcher.parse(token);
        Set<BlockPos> tried = new HashSet<>();
        Supplier<BlockPos> nearest = () -> {
            BlockPos center = player().blockPosition();
            BlockPos[] best = {null};
            BlockScan.around(minecraft().level, center, MINE_SEARCH_RADIUS, matcher, false, (pos, state) -> {
                if (!tried.contains(pos) && (best[0] == null || pos.distSqr(center) < best[0].distSqr(center))) {
                    best[0] = pos.immutable();
                }
            });
            if (best[0] != null) {
                tried.add(best[0]);
            }
            return best[0];
        };
        return new Dig(session, nearest, wanted, true, token);
    }

    /**
     * Digs blocks one after another: walks into reach when allowed, picks the best tool, aims at the block and holds
     * attack until it breaks. The crosshair must hit the block itself, so blocks behind others are skipped.
     */
    private static final class Dig implements Action {
        private final int session;
        private final Supplier<BlockPos> targets;
        private final int wanted;
        private final boolean walk;
        private final String what;
        private BlockPos current;
        private Supplier<Vec3> aim;
        private BlockState original;
        private Navigator walking;
        private int mined;
        private int blockedTicks;
        private int digTicks;
        private String minedId;

        Dig(int session, Supplier<BlockPos> targets, int wanted, boolean walk, String what) {
            this.session = session;
            this.targets = targets;
            this.wanted = wanted;
            this.walk = walk;
            this.what = what;
        }

        @Override
        public Result tick() throws CommandException {
            if (current == null) {
                current = targets.get();
                if (current == null) {
                    return finish();
                }
                original = minecraft().level.getBlockState(current);
                aim = seen(minecraft().level, Vec3.atCenterOf(current));
                walking = null;
                blockedTicks = 0;
                digTicks = 0;
            }
            BlockState state = minecraft().level.getBlockState(current);
            if (state != original && !state.is(original.getBlock()) || state.isAir()) {
                InputState.release(options().keyAttack);
                mined++;
                minedId = Ids.of(original);
                current = null;
                return mined >= wanted ? finish() : null;
            }
            Vec3 center = aim.get();
            boolean inReach = center.distanceTo(player().getEyePosition()) <= player().blockInteractionRange() - 0.5;
            if (!inReach) {
                if (!walk) {
                    throw new CommandException("out_of_reach", Rel.of(current).toString());
                }
                if (walking == null) {
                    BlockPos target = current;
                    walking = new Navigator(session, () -> Vec3.atBottomCenterOf(target), 3);
                }
                try {
                    walking.tick();
                } catch (CommandException e) {
                    walking.cancel();
                    current = null;
                }
                return null;
            }
            if (walking != null) {
                walking.cancel();
                walking = null;
            }
            if (digTicks++ == 0) {
                InventoryOps.selectBestTool(state);
                InventoryOps.selectItemThatCanBreak(state, current);
            }
            lookAt(center);
            HitResult hit = minecraft().hitResult;
            boolean aimed = hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK
                && blockHit.getBlockPos().equals(current);
            if (!aimed) {
                InputState.release(options().keyAttack);
                if (++blockedTicks > 10) {
                    if (!walk) {
                        throw new CommandException("out_of_reach", Rel.of(current) + " is behind another block");
                    }
                    current = null;
                }
                return null;
            }
            InputState.hold(session, options().keyAttack);
            return null;
        }

        private Result finish() throws CommandException {
            InputState.release(options().keyAttack);
            if (mined == 0) {
                throw new CommandException("no_block", what == null ? "nothing mined" : what);
            }
            String label = mined + "/" + wanted;
            return Result.ok(new Fields().put("mined", mined, "mine " + label).bare("block", minedId == null ? "?"
                : minedId));
        }

        @Override
        public void cancel() {
            InputState.release(options().keyAttack);
            if (walking != null) {
                walking.cancel();
            }
        }

        @Override
        public String label() {
            return "mine";
        }
    }

    private Action place(CommandContext context, Args args) throws CommandException {
        closeScreen();
        Item item = InventoryOps.item(args.word("item"));
        BlockPos pos = Rel.block(context.resolve(args.position("pos")));
        var level = minecraft().level;
        if (!level.getBlockState(pos).canBeReplaced()) {
            throw new CommandException("syntax", Rel.of(pos) + " is occupied by " + Ids.of(level.getBlockState(pos)));
        }
        Direction support = null;
        for (Direction direction : Direction.values()) {
            BlockPos neighbour = pos.relative(direction);
            if (!level.getBlockState(neighbour).canBeReplaced()) {
                support = direction;
                break;
            }
        }
        if (support == null) {
            throw new CommandException("no_block", "nothing next to " + Rel.of(pos) + " to place against");
        }
        InventoryOps.select(item);
        BlockPos against = pos.relative(support);
        Supplier<Vec3> point = seen(level, Vec3.atCenterOf(against)
            .add(Vec3.atLowerCornerOf(support.getOpposite().getNormal()).scale(0.5)));
        int[] step = {0};
        return named("place", () -> {
            step[0]++;
            if (step[0] == 1) {
                lookAt(point.get());
                return null;
            }
            if (step[0] == 2) {
                HitResult hit = minecraft().hitResult;
                if (!(hit instanceof BlockHitResult blockHit) || !blockHit.getBlockPos().equals(against)) {
                    throw new CommandException("out_of_reach", "cannot see the face to place against");
                }
                // A sub-level under way moves on before the click's own pick.
                lookAt(point.get());
                click(options().keyUse);
                return null;
            }
            if (step[0] < 5 && level.getBlockState(pos).canBeReplaced()) {
                return null;
            }
            BlockState placed = level.getBlockState(pos);
            if (placed.canBeReplaced()) {
                throw new CommandException("no_block", "nothing was placed");
            }
            return Result.ok(new Fields().bare("block", Ids.of(placed)).bare("at", Rel.of(pos)));
        });
    }

    private Action select(CommandContext context, Args args) throws CommandException {
        closeScreen();
        if (args.type("what") == ArgType.INT) {
            int slot = args.integer("what", 0);
            if (slot < 0 || slot > 8) {
                throw new CommandException("syntax", "hotbar slots are 0-8, not " + slot);
            }
            InventoryOps.selectSlot(slot);
        } else {
            InventoryOps.select(InventoryOps.item(args.word("what")));
        }
        return () -> Result.ok(new Fields().put("hand", Ids.of(player().getMainHandItem()),
            "hand:" + Ids.of(player().getMainHandItem())));
    }

    private Action swap(CommandContext context, Args args) throws CommandException {
        closeScreen();
        int a = InventoryOps.menuSlotOf(args.word("a"));
        int b = InventoryOps.menuSlotOf(args.word("b"));
        InventoryOps.click(a, 0, ClickType.PICKUP);
        InventoryOps.click(b, 0, ClickType.PICKUP);
        InventoryOps.click(a, 0, ClickType.PICKUP);
        return () -> Result.ok();
    }

    private Action drop(CommandContext context, Args args) throws CommandException {
        closeScreen();
        String what = args.word("what");
        int menuSlot;
        if (args.type("what") != ArgType.ID) {
            menuSlot = InventoryOps.menuSlotOf(what);
        } else {
            int index = InventoryOps.find(InventoryOps.item(what));
            if (index < 0) {
                throw new CommandException("no_item", what);
            }
            menuSlot = InventoryOps.menuSlot(index);
        }
        ItemStack stack = player().inventoryMenu.getSlot(menuSlot).getItem();
        if (stack.isEmpty()) {
            throw new CommandException("no_item", "slot " + what + " is empty");
        }
        // Read before throwing: a throw empties this same stack.
        String item = Ids.of(stack);
        int count = Math.min(args.integer("count", stack.getCount()), stack.getCount());
        if (count >= stack.getCount()) {
            InventoryOps.click(menuSlot, 1, ClickType.THROW);
        } else {
            for (int index = 0; index < count; index++) {
                InventoryOps.click(menuSlot, 0, ClickType.THROW);
            }
        }
        return () -> Result.ok(new Fields().bare("item", item).put("count", count, "x" + count));
    }

    private Action offhand(CommandContext context, Args args) throws CommandException {
        closeScreen();
        Item item = InventoryOps.item(args.word("item"));
        int index = InventoryOps.find(item);
        if (index < 0) {
            throw new CommandException("no_item", args.word("item"));
        }
        InventoryOps.click(InventoryOps.menuSlot(index), InventoryOps.OFFHAND_BUTTON, ClickType.SWAP);
        return () -> Result.ok();
    }

    private Action equip(CommandContext context, Args args) throws CommandException {
        closeScreen();
        Item item = InventoryOps.item(args.word("item"));
        int index = InventoryOps.find(item);
        if (index < 0) {
            throw new CommandException("no_item", args.word("item"));
        }
        InventoryOps.click(InventoryOps.menuSlot(index), 0, ClickType.QUICK_MOVE);
        return () -> {
            for (int slot = InventoryMenu.ARMOR_SLOT_START; slot < InventoryMenu.ARMOR_SLOT_END; slot++) {
                if (player().inventoryMenu.getSlot(slot).getItem().is(item)) {
                    return Result.ok();
                }
            }
            throw new CommandException("no_item", args.word("item") + " is not armor or the slot is taken");
        };
    }

    private Action chat(CommandContext context, Args args) {
        player().connection.sendChat(args.text("text"));
        return () -> Result.ok();
    }

    private Action command(CommandContext context, Args args) {
        String command = args.text("command");
        player().connection.sendCommand(command.startsWith("/") ? command.substring(1) : command);
        return () -> Result.ok();
    }

    /** An action from a lambda, with a label for the HUD. */
    static Action named(String label, Action body) {
        return new Action() {
            @Override
            public Result tick() throws CommandException {
                return body.tick();
            }

            @Override
            public String label() {
                return label;
            }
        };
    }
}

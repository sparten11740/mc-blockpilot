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
import dev.blockpilot.api.Position;
import dev.blockpilot.api.Result;
import dev.blockpilot.game.input.InputState;
import dev.blockpilot.game.path.Navigator;
import dev.blockpilot.mixin.KeyMappingAccessor;
import dev.blockpilot.protocol.CommandRegistry;
import java.util.HashSet;
import java.util.Set;
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

    void register(CommandRegistry registry) {
        registry.register(CommandSpec.action("face", this::face).needsWorld().group("movement")
            .arg("target", ArgType.TARGET)
            .help("turns to look at a point (decimals allowed, block centers for whole numbers) or an entity; "
                + "returns what the crosshair then hits").example("face 12.5,70.2,-3.4"));
        registry.register(CommandSpec.action("turn", this::turn).needsWorld().group("movement")
            .arg("yaw", ArgType.NUMBER).arg("pitch", ArgType.NUMBER).optionalArg("ticks", ArgType.DURATION)
            .help("sets the camera to yaw/pitch (degrees), optionally smoothed over a duration")
            .example("turn 90 20 10t"));
        registry.register(CommandSpec.action("move", this::move).needsWorld().group("movement")
            .choice("dir", false, "forward", "back", "left", "right").arg("amount", ArgType.WORD)
            .help("holds a movement key for <blocks> or <Nt> ticks").example("move forward 3"));
        registry.register(CommandSpec.action("jump", (context, args) -> pulse(context, options().keyJump, 2, "jump"))
            .needsWorld().group("movement").help("one jump").example("jump"));
        registry.register(CommandSpec.action("sneak", (context, args) -> toggle(context, options().keyShift,
            args.word("state"))).needsWorld().group("movement").choice("state", false, "on", "off")
            .help("holds or releases sneak").example("sneak on"));
        registry.register(CommandSpec.action("sprint", (context, args) -> toggle(context, options().keySprint,
            args.word("state"))).needsWorld().group("movement").choice("state", false, "on", "off")
            .help("holds or releases sprint").example("sprint on"));
        registry.register(CommandSpec.action("fly", this::fly).needsWorld().group("movement")
            .choice("state", false, "on", "off")
            .help("starts or stops flying where the game mode allows it (creative, spectator)").example("fly on"));
        registry.register(CommandSpec.action("goto", this::navigate).needsWorld().group("movement")
            .arg("target", ArgType.TARGET).option("near", "stop within this many blocks")
            .help("walks there through loaded chunks (walk, step up 1, drop up to 3, swim, ladders; never digs)")
            .field("took", "seconds").example("goto 120,64,-30"));
        registry.register(CommandSpec.action("use", this::use).needsWorld().group("interact")
            .optionalArg("target", ArgType.TARGET).choice("sneak", true, "sneak")
            .option("hold", "keep the use key down for a duration (eat, draw a bow, charge)")
            .help("right-clicks: aims first if given a target, else whatever the crosshair hits; returns the hit")
            .field("aim", "what was clicked").example("use ~1,0,0 sneak"));
        registry.register(CommandSpec.action("attack", this::attack).needsWorld().group("interact")
            .optionalArg("target", ArgType.WORD).optionalArg("count", ArgType.COUNT)
            .help("left-clicks the crosshair, or fights e<id> / the nearest of an entity type until it dies, "
                + "at full attack cooldown").example("attack e812"));
        registry.register(CommandSpec.action("mine", this::mine).needsWorld().group("interact")
            .optionalArg("target", ArgType.WORD).optionalArg("count", ArgType.COUNT)
            .help("digs the block at <pos> (in reach), what the crosshair hits (no target), or walks to and digs "
                + "the n nearest <block|#tag>; picks the best tool").example("mine #logs x6"));
        registry.register(CommandSpec.action("place", this::place).needsWorld().group("interact")
            .arg("item", ArgType.WORD).arg("pos", ArgType.POSITION)
            .help("places the item at pos against an adjacent block").example("place torch ~1,0,0"));
        registry.register(CommandSpec.action("select", this::select).needsWorld().group("inventory")
            .arg("what", ArgType.WORD).help("selects hotbar slot 0-8, or puts the item into the selected slot")
            .example("select iron_pickaxe"));
        registry.register(CommandSpec.action("swap", this::swap).needsWorld().group("inventory")
            .arg("a", ArgType.WORD).arg("b", ArgType.WORD)
            .help("swaps two inventory slots (0-35, head, chest, legs, feet, offhand)").example("swap 0 9"));
        registry.register(CommandSpec.action("drop", this::drop).needsWorld().group("inventory")
            .arg("what", ArgType.WORD).optionalArg("count", ArgType.INT)
            .help("drops an item or a slot's stack (all by default)").example("drop dirt 16"));
        registry.register(CommandSpec.action("offhand", this::offhand).needsWorld().group("inventory")
            .arg("item", ArgType.WORD).help("moves the item to the offhand").example("offhand shield"));
        registry.register(CommandSpec.action("equip", this::equip).needsWorld().group("inventory")
            .arg("item", ArgType.WORD).help("puts on an armor item").example("equip iron_helmet"));
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

    /** A point to look at: an entity's center, a block's center for whole coordinates, else the exact point. */
    static Supplier<Vec3> point(CommandContext context, String token) throws CommandException {
        EntityRef ref = EntityRef.parse(token);
        if (ref != null) {
            Entity entity = minecraft().level.getEntity(ref.id());
            if (entity == null) {
                throw new CommandException("no_entity", token);
            }
            return () -> entity.isRemoved() ? null : entity.getBoundingBox().getCenter();
        }
        Position parsed = Position.parse(token);
        if (parsed == null) {
            throw new CommandException("syntax", "position or e<id> expected, got " + token);
        }
        Position absolute = context.resolve(parsed);
        boolean whole = absolute.x() == Math.floor(absolute.x()) && absolute.y() == Math.floor(absolute.y())
            && absolute.z() == Math.floor(absolute.z());
        Vec3 target = whole ? Vec3.atCenterOf(BlockPos.containing(absolute.x(), absolute.y(), absolute.z()))
            : new Vec3(absolute.x(), absolute.y(), absolute.z());
        return () -> target;
    }

    static void lookAt(Vec3 point) {
        player().lookAt(EntityAnchorArgument.Anchor.EYES, point);
    }

    private Action face(CommandContext context, Args args) throws CommandException {
        closeScreen();
        Supplier<Vec3> target = point(context, args.word("target"));
        int[] step = {0};
        return named("face", () -> {
            Vec3 point = target.get();
            if (point == null) {
                throw new CommandException("no_entity", args.word("target"));
            }
            if (step[0]++ == 0) {
                lookAt(point);
                return null;
            }
            return Result.ok(hit());
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
        String amount = args.word("amount");
        Duration duration = Duration.parse(amount);
        double blocks;
        try {
            blocks = duration == null ? Double.parseDouble(amount) : 0;
        } catch (NumberFormatException e) {
            throw new CommandException("syntax", "move <dir> <blocks|Nt>, got " + amount);
        }
        int session = context.sessionId();
        Vec3 start = player().position();
        int[] ticks = {0};
        return new Action() {
            @Override
            public Result tick() {
                InputState.hold(session, key);
                ticks[0]++;
                double moved = Math.hypot(player().getX() - start.x, player().getZ() - start.z);
                boolean done = duration != null ? ticks[0] >= duration.ticks() : moved >= blocks;
                if (!done) {
                    return null;
                }
                InputState.release(key);
                return Result.ok(new Fields().put("moved", Math.round(moved * 10) / 10.0,
                    "moved " + Math.round(moved * 10) / 10.0));
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
        player.getAbilities().flying = args.word("state").equals("on");
        player.onUpdateAbilities();
        return () -> Result.ok(new Fields().put("flying", player.getAbilities().flying,
            player.getAbilities().flying ? "flying" : "not flying"));
    }

    private Action navigate(CommandContext context, Args args) throws CommandException {
        closeScreen();
        String token = args.word("target");
        double near = args.numberOption("near", EntityRef.parse(token) != null ? 2 : 0);
        Supplier<Vec3> target;
        EntityRef ref = EntityRef.parse(token);
        if (ref != null) {
            Entity entity = minecraft().level.getEntity(ref.id());
            if (entity == null) {
                throw new CommandException("no_entity", token);
            }
            target = () -> entity.isRemoved() ? null : entity.position();
        } else {
            Position absolute = context.resolve(Position.parse(token));
            Vec3 goal = Vec3.atBottomCenterOf(BlockPos.containing(absolute.x(), absolute.y(), absolute.z()));
            target = () -> goal;
        }
        return new Navigator(context.sessionId(), target, near);
    }

    private Action use(CommandContext context, Args args) throws CommandException {
        closeScreen();
        Supplier<Vec3> target = args.has("target") ? point(context, args.word("target")) : null;
        boolean sneak = args.has("sneak");
        Duration hold = args.durationOption("hold");
        int session = context.sessionId();
        KeyMapping use = options().keyUse;
        KeyMapping shift = options().keyShift;
        return new Action() {
            private int step;
            private int held;
            private Fields hit;

            @Override
            public Result tick() throws CommandException {
                if (step == 0) {
                    step++;
                    if (target != null) {
                        Vec3 point = target.get();
                        if (point == null) {
                            throw new CommandException("no_entity", args.word("target"));
                        }
                        lookAt(point);
                    }
                    if (sneak) {
                        InputState.hold(session, shift);
                    }
                    if (target != null || sneak) {
                        return null;
                    }
                }
                if (step == 1) {
                    step++;
                    hit = hit();
                    if (hold != null) {
                        InputState.hold(session, use);
                    } else {
                        click(use);
                    }
                    return null;
                }
                if (hold != null && ++held < hold.ticks()) {
                    return null;
                }
                release();
                return Result.ok(hit);
            }

            private void release() {
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
        EntityRef ref = EntityRef.parse(token);
        int wanted = args.count("count", 1);
        Supplier<Entity> next;
        if (ref != null) {
            next = () -> minecraft().level.getEntity(ref.id());
        } else {
            var id = Ids.parse(token);
            // The entity registry answers unknown ids with its default (pig), so check first.
            if (!BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
                throw new CommandException("syntax", "attack e<id> or an entity type, got " + token);
            }
            var type = BuiltInRegistries.ENTITY_TYPE.get(id);
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
        Position position = Position.parse(token);
        if (position != null) {
            BlockPos pos = Rel.block(context.resolve(position));
            if (Vec3.atCenterOf(pos).distanceTo(player().getEyePosition()) > player().blockInteractionRange()) {
                throw new CommandException("out_of_reach", Rel.of(pos).toString());
            }
            return new Dig(session, () -> pos, 1, false, null);
        }
        BlockMatcher matcher = BlockMatcher.parse(token);
        Set<BlockPos> tried = new HashSet<>();
        Supplier<BlockPos> nearest = () -> {
            BlockPos center = player().blockPosition();
            BlockPos[] best = {null};
            PerceptionCommands.scan(minecraft().level, center, MINE_SEARCH_RADIUS, matcher, false, (pos, state) -> {
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
            Vec3 center = Vec3.atCenterOf(current);
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
        Vec3 point = Vec3.atCenterOf(against).add(Vec3.atLowerCornerOf(support.getOpposite().getNormal()).scale(0.5));
        int[] step = {0};
        return named("place", () -> {
            step[0]++;
            if (step[0] == 1) {
                lookAt(point);
                return null;
            }
            if (step[0] == 2) {
                HitResult hit = minecraft().hitResult;
                if (!(hit instanceof BlockHitResult blockHit) || !blockHit.getBlockPos().equals(against)) {
                    throw new CommandException("out_of_reach", "cannot see the face to place against");
                }
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
        String what = args.word("what");
        if (what.matches("[0-8]")) {
            player().getInventory().selected = Integer.parseInt(what);
        } else {
            InventoryOps.select(InventoryOps.item(what));
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
        if (what.matches("\\d+|head|chest|legs|feet|offhand")) {
            menuSlot = InventoryOps.menuSlotOf(what);
        } else {
            int index = InventoryOps.find(InventoryOps.item(what));
            if (index < 0) {
                throw new CommandException("no_item", what);
            }
            menuSlot = InventoryOps.menuSlot(index);
        }
        ItemStack stack = player().inventoryMenu.getSlot(menuSlot).getItem();
        int count = Math.min(args.integer("count", stack.getCount()), stack.getCount());
        if (count >= stack.getCount()) {
            InventoryOps.click(menuSlot, 1, ClickType.THROW);
        } else {
            for (int index = 0; index < count; index++) {
                InventoryOps.click(menuSlot, 0, ClickType.THROW);
            }
        }
        return () -> Result.ok(new Fields().bare("item", Ids.of(stack)).put("count", count, "x" + count));
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

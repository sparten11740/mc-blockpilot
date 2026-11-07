package dev.blockpilot.game;

import static dev.blockpilot.game.PlayerActions.PLOT_AIM_HELP;
import static dev.blockpilot.game.PlayerActions.WITHIN_BLOCK;
import static dev.blockpilot.game.PlayerActions.WITHIN_BLOCK_HELP;
import static dev.blockpilot.game.PlayerActions.click;
import static dev.blockpilot.game.PlayerActions.closeScreen;
import static dev.blockpilot.game.PlayerActions.hit;
import static dev.blockpilot.game.PlayerActions.lookAt;
import static dev.blockpilot.game.PlayerActions.minecraft;
import static dev.blockpilot.game.PlayerActions.named;
import static dev.blockpilot.game.PlayerActions.options;
import static dev.blockpilot.game.PlayerActions.player;
import static dev.blockpilot.game.PlayerActions.point;
import static dev.blockpilot.game.PlayerActions.seen;
import static dev.blockpilot.game.PlayerActions.target;

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
import dev.blockpilot.game.common.BlockMatcher;
import dev.blockpilot.game.common.BlockScan;
import dev.blockpilot.game.common.Ids;
import dev.blockpilot.game.common.Shapes;
import dev.blockpilot.game.input.AimedClick;
import dev.blockpilot.game.input.InputState;
import dev.blockpilot.game.path.Pathfinder;
import dev.blockpilot.game.path.Pathfinders;
import dev.blockpilot.game.sable.Rel;
import dev.blockpilot.game.sable.SableSupport;
import dev.blockpilot.protocol.CommandRegistry;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.client.KeyMapping;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Commands that click on the world: they aim first, and click through the game's own key mappings. */
final class InteractCommands {
    private static final int MINE_SEARCH_RADIUS = 32;
    /** How long {@code use} re-aims at its target for the crosshair to rest on it before it gives up. */
    private static final int MAX_AIM_TICKS = 10;

    void register(CommandRegistry registry) {
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
                + "the reply names as sneak_item. " + PLOT_AIM_HELP + "After err obstructed at a plot position, "
                + "sable:tp to that position moves the player to the nearest spot from which it is in sight, if there "
                + "is one (else it answers hidden). " + WITHIN_BLOCK_HELP)
            .field("aim", "what was clicked")
            .field("off_target", "with any: the target, when the click went to something else")
            .field("sneak_item", "with sneak: the item in hand that took the click instead of the block")
            .example("use ~1,0,0 sneak"));
        registry.register(CommandSpec.action("attack", this::attack).needsWorld().group("interact")
            .optionalArg("target", ArgType.ENTITY, ArgType.ID).optionalArg("count", ArgType.COUNT)
            .help("left-clicks the crosshair, or fights e<id> / the nearest of an entity type until it dies, "
                + "at full attack cooldown").example("attack e812"));
        registry.register(CommandSpec.action("mine", this::mine).needsWorld().group("interact")
            .optionalArg("target", ArgType.POSITION, ArgType.TAG, ArgType.PATTERN, ArgType.ID, ArgType.BLOCK_STATE)
            .optionalArg("count", ArgType.COUNT)
            .help("digs the block at <pos> (in reach), what the crosshair hits (no target), or walks to and digs "
                + "the n nearest <block|#tag>, also with states (oak_log[axis=x]); picks the best tool. A block the "
                + "player can't break (bedrock outside creative, adventure mode) fails at once with err unbreakable; "
                + "by type such blocks are passed over. A block at <pos> behind another fails with err out_of_reach "
                + "naming what the crosshair hits")
            .example("mine #logs x6"));
        registry.register(CommandSpec.action("place", this::place).needsWorld().group("interact")
            .arg("item", ArgType.ID).arg("pos", ArgType.POSITION)
            .help("places the item at pos against an adjacent block, aiming at the face of that block's shape (a "
                + "carpet's top); when the crosshair hits something else, or another face, it fails with err "
                + "out_of_reach naming what it hits, and places nothing").example("place torch ~1,0,0"));
    }

    /** The main hand's item, else the off hand's, or null with both hands empty. */
    private static String heldItem() {
        ItemStack main = player().getMainHandItem();
        ItemStack off = player().getOffhandItem();
        return !main.isEmpty() ? Ids.of(main) : !off.isEmpty() ? Ids.of(off) : null;
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
        String what = crosshair(hit);
        if (what != null && !target.on().test(hit)) {
            return new CommandException("obstructed", what);
        }
        return new CommandException("out_of_reach", String.valueOf(target.name().get()));
    }

    /** What the crosshair hits: a block and its position, or an entity and its reference; null for nothing. */
    private static String crosshair(HitResult hit) {
        if (hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = block.getBlockPos();
            return Ids.of(minecraft().level.getBlockState(pos)) + " " + Rel.of(pos);
        }
        if (hit instanceof EntityHitResult entity && hit.getType() == HitResult.Type.ENTITY) {
            return Ids.of(entity.getEntity()) + " e" + entity.getEntity().getId();
        }
        return null;
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
            Vec3 center = seen(minecraft().level, Shapes.centre(minecraft().level, pos)).get();
            if (center.distanceTo(player().getEyePosition()) > player().blockInteractionRange()) {
                throw new CommandException("out_of_reach", Rel.of(pos).toString());
            }
            return new Dig(session, () -> pos, 1, false, null);
        }
        BlockMatcher matcher = BlockMatcher.parse(token);
        Set<BlockPos> tried = new HashSet<>();
        Dig.Targets nearest = () -> {
            BlockPos center = player().blockPosition();
            BlockPos[] best = {null};
            String[] unbreakable = {null};
            BlockScan.around(minecraft().level, center, MINE_SEARCH_RADIUS, matcher, false, (pos, state) -> {
                if (tried.contains(pos)) {
                    return;
                }
                if (!breakable(state, pos)) {
                    unbreakable[0] = Ids.of(state);
                } else if (best[0] == null || pos.distSqr(center) < best[0].distSqr(center)) {
                    best[0] = pos.immutable();
                }
            });
            if (best[0] == null && tried.isEmpty() && unbreakable[0] != null) {
                throw new CommandException("unbreakable", unbreakable[0]);
            }
            if (best[0] != null) {
                tried.add(best[0]);
            }
            return best[0];
        };
        return new Dig(session, nearest, wanted, true, token);
    }

    /**
     * Whether holding attack can break the block: creative breaks any, otherwise the game mode must allow it and each
     * tick of digging must make progress (none for bedrock, or a block a mod makes unbreakable).
     */
    private static boolean breakable(BlockState state, BlockPos pos) {
        GameType mode = minecraft().gameMode.getPlayerMode();
        return mode.isCreative() || !player().blockActionRestricted(minecraft().level, pos, mode)
            && state.getDestroyProgress(player(), minecraft().level, pos) > 0;
    }

    /**
     * Digs blocks one after another: walks into reach when allowed, picks the best tool, aims at the block and holds
     * attack until it breaks. The crosshair must hit the block itself, so blocks behind others are skipped.
     */
    private static final class Dig implements Action {
        /** The next block to dig, or null when there is none. */
        @FunctionalInterface
        interface Targets {
            BlockPos next() throws CommandException;
        }

        /** How near, in blocks, a walk into reach goes. */
        private static final double REACH_WALK = 3;

        private final int session;
        private final Targets targets;
        private final int wanted;
        private final boolean walk;
        private final String what;
        private BlockPos current;
        private Supplier<Vec3> aim;
        private BlockState original;
        private Action walking;
        /** The pathfinder that walked into reach when it isn't the built-in one, for by= in the reply; else null. */
        private String walkedBy;
        private int mined;
        private int blockedTicks;
        private int digTicks;
        private boolean attacking;
        private String minedId;

        Dig(int session, Targets targets, int wanted, boolean walk, String what) {
            this.session = session;
            this.targets = targets;
            this.wanted = wanted;
            this.walk = walk;
            this.what = what;
        }

        @Override
        public Result tick() throws CommandException {
            if (current == null) {
                current = targets.next();
                if (current == null) {
                    return finish();
                }
                aimFor(minecraft().level.getBlockState(current));
                walking = null;
                blockedTicks = 0;
                attacking = false;
            }
            BlockState state = minecraft().level.getBlockState(current);
            if (state != original && !state.is(original.getBlock()) || state.isAir()) {
                if (attacking) {
                    InputState.release(options().keyAttack);
                    mined++;
                    minedId = Ids.of(original);
                    current = null;
                    return mined >= wanted ? finish() : null;
                }
                // Not the player's doing: grass spread onto dirt, a block fell, another player was first.
                if (walk) {
                    stopWalking();
                    current = null;
                    return null;
                }
                if (state.isAir()) {
                    throw new CommandException("no_block", Rel.of(current) + " is air");
                }
                aimFor(state);
            }
            Vec3 center = aim.get();
            boolean inReach = center.distanceTo(player().getEyePosition()) <= player().blockInteractionRange() - 0.5;
            if (!inReach) {
                if (!walk) {
                    throw new CommandException("out_of_reach", Rel.of(current).toString());
                }
                if (walking == null) {
                    walking = walkTo(current, REACH_WALK);
                }
                try {
                    walking.tick();
                } catch (CommandException e) {
                    walking.cancel();
                    current = null;
                }
                return null;
            }
            stopWalking();
            if (digTicks++ == 0) {
                InventoryOps.selectBestTool(state);
                InventoryOps.selectItemThatCanBreak(state, current);
                if (!breakable(state, current)) {
                    if (!walk) {
                        throw new CommandException("unbreakable", Ids.of(state) + " " + Rel.of(current));
                    }
                    current = null;
                    return null;
                }
            }
            lookAt(center);
            HitResult hit = minecraft().hitResult;
            boolean aimed = hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK
                && blockHit.getBlockPos().equals(current);
            if (!aimed) {
                InputState.release(options().keyAttack);
                attacking = false;
                if (++blockedTicks > 10) {
                    if (!walk) {
                        String blocker = crosshair(hit);
                        throw new CommandException("out_of_reach", Rel.of(current) + (blocker == null
                            ? " is out of sight; the crosshair hits nothing" : " is behind " + blocker));
                    }
                    current = null;
                }
                return null;
            }
            attacking = true;
            InputState.hold(session, options().keyAttack);
            return null;
        }

        private void aimFor(BlockState state) {
            original = state;
            // The shape's centre, not the cell's: over a carpet or a rail that is in the air above it.
            aim = seen(minecraft().level, Shapes.centre(minecraft().level, current));
            digTicks = 0;
        }

        private Action walkTo(BlockPos block, double near) {
            Pathfinder pathfinder = Pathfinders.installed().current();
            if (!Pathfinders.builtIn(pathfinder)) {
                walkedBy = pathfinder.name();
            }
            return pathfinder.walk(session, Pathfinders.positions(() -> Vec3.atBottomCenterOf(block)), near);
        }

        private void stopWalking() {
            if (walking != null) {
                walking.cancel();
                walking = null;
            }
        }

        private Result finish() throws CommandException {
            InputState.release(options().keyAttack);
            if (mined == 0) {
                throw new CommandException("no_block", what == null ? "nothing mined" : what);
            }
            String label = mined + "/" + wanted;
            Fields fields = new Fields().put("mined", mined, "mine " + label).bare("block", minedId == null ? "?"
                : minedId);
            return Result.ok(walkedBy == null ? fields : fields.put("by", walkedBy));
        }

        @Override
        public void cancel() {
            InputState.release(options().keyAttack);
            stopWalking();
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
            BlockPos above = pos.above();
            String free = level.getBlockState(above).canBeReplaced() ? "; " + Rel.of(above) + " above is free" : "";
            throw new CommandException("syntax", Rel.of(pos) + " is occupied by " + Ids.of(level.getBlockState(pos))
                + free);
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
        String id = Ids.of(new ItemStack(item));
        if (InventoryOps.find(item) < 0) {
            throw new CommandException("no_item",
                id + "; not in the inventory, give it first (cmd give @s " + id + ")");
        }
        InventoryOps.select(item);
        BlockPos against = pos.relative(support);
        Direction face = support.getOpposite();
        Supplier<Vec3> point = seen(level, Shapes.faceToward(level, against, face));
        int[] step = {0};
        return named("place", () -> {
            step[0]++;
            if (step[0] == 1) {
                lookAt(point.get());
                return null;
            }
            if (step[0] == 2) {
                HitResult hit = minecraft().hitResult;
                // The click places beside whichever face it hits, so another face of the block would put it elsewhere.
                if (!(hit instanceof BlockHitResult blockHit) || !blockHit.getBlockPos().equals(against)
                    || blockHit.getDirection() != face) {
                    String blocker = crosshair(hit);
                    String side = hit instanceof BlockHitResult faceHit && hit.getType() == HitResult.Type.BLOCK
                        ? " on its " + faceHit.getDirection().getName() + " face" : "";
                    throw new CommandException("out_of_reach", "cannot see the face to place against; the crosshair "
                        + "hits " + (blocker == null ? "nothing" : blocker + side));
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
}

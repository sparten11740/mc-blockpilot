package dev.blockpilot.game;

import dev.blockpilot.api.Action;
import dev.blockpilot.api.ArgType;
import dev.blockpilot.api.Args;
import dev.blockpilot.api.CommandContext;
import dev.blockpilot.api.CommandException;
import dev.blockpilot.api.Fields;
import dev.blockpilot.api.Form;
import dev.blockpilot.api.Position;
import dev.blockpilot.api.Result;
import dev.blockpilot.game.common.BlockFrame;
import dev.blockpilot.game.common.ClientScreens;
import dev.blockpilot.game.common.Shapes;
import dev.blockpilot.game.sable.SableSupport;
import dev.blockpilot.game.sable.SubLevels;
import dev.blockpilot.mixin.KeyMappingAccessor;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * What commands that act as the player share: they turn the camera, hold movement keys and click use/attack through the
 * game's own key mappings, so everything reaches the server and other mods as ordinary player input.
 */
final class PlayerActions {
    /** {@code at <u,v,w>}: a point inside the target block, in the block's own frame. */
    static final Consumer<Form> WITHIN_BLOCK = at -> at.keyword("at", "at").arg("point", ArgType.POSITION);
    static final String WITHIN_BLOCK_HELP = "at u,v,w aims at that point of the block, each 0 to 1 in the "
        + "frame its model is drawn in facing north (u west to east, v up, w north to south), turned with the block's "
        + "facing, so seen from the model's north side, usually the block's front, u runs right to left; status shows "
        + "hit=u,v,w for the point under the crosshair";
    static final String PLOT_AIM_HELP = "With Sable, a plot position follows its sub-level each tick, so the "
        + "aim holds on a moving ship, where a world position is left behind. ";

    private PlayerActions() {
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

    /** How far the player reaches blocks: its attribute, or on 1.20.1 the game mode's pick range. */
    static double blockReach() {
        //? if >=1.21 {
        return player().blockInteractionRange();
        //?} else {
        /*return minecraft().gameMode.getPickRange();
        *///?}
    }

    /** How far the player reaches entities: its attribute, or on 1.20.1 the 3 blocks, 6 in creative, the pick uses. */
    static double entityReach() {
        //? if >=1.21 {
        return player().entityInteractionRange();
        //?} else {
        /*return minecraft().gameMode.hasFarPickRange() ? 6 : 3;
        *///?}
    }

    /**
     * Closes an open screen (container, inventory, menu) the way Esc would, so world actions and inventory clicks
     * reach the world and the player's own inventory instead of a screen left open by an earlier command.
     */
    static void closeScreen() {
        Minecraft minecraft = minecraft();
        if (ClientScreens.current() == null || minecraft.player == null) {
            return;
        }
        if (minecraft.player.containerMenu != minecraft.player.inventoryMenu) {
            minecraft.player.closeContainer();
        } else {
            ClientScreens.open(null);
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
    static Supplier<Vec3> seen(Level level, Vec3 point) {
        SubLevels.Info subLevel = SableSupport.subLevels().at(level, BlockPos.containing(point));
        if (subLevel != null) {
            return () -> SableSupport.subLevels().toWorld(level, point).add(lead(level, point, subLevel.id()));
        }
        return () -> point;
    }

    /**
     * Whether the player sees the block's face at the point, as the crosshair's pick would once turned to it: its ray,
     * from the eyes through the point out to reach, stopping at the first block outline and passing through fluids,
     * meets that block on that face first. A point at the eyes is never in sight, as no ray leads to it.
     */
    static boolean seesFace(Level level, BlockPos block, Direction face, Vec3 point) {
        LocalPlayer player = player();
        Vec3 eyes = player.getEyePosition();
        Vec3 toward = point.subtract(eyes);
        if (toward.lengthSqr() == 0) {
            return false;
        }
        Vec3 end = eyes.add(toward.normalize().scale(blockReach()));
        BlockHitResult hit = level.clip(new ClipContext(eyes, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE,
            player));
        return hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(block) && hit.getDirection() == face;
    }

    /**
     * With {@code at <u,v,w>} that point of the block in its own frame; for whole coordinates the centre of the block's
     * shape (a lever's base, not the air above it); else the point itself.
     */
    static Vec3 target(Level level, Position absolute, Args args) throws CommandException {
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

    static void lookAt(Vec3 point) {
        player().lookAt(EntityAnchorArgument.Anchor.EYES, point);
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

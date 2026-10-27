package dev.blockpilot.game.path;

import dev.blockpilot.api.Action;
import dev.blockpilot.api.CommandException;
import dev.blockpilot.api.Duration;
import dev.blockpilot.api.Fields;
import dev.blockpilot.api.Result;
import dev.blockpilot.game.input.InputState;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Walks the player to a target along a computed path: faces the next cell, holds forward, jumps up steps, and
 * re-plans when pushed off the path. The target may move (following an entity).
 */
public final class Navigator implements Action {
    private static final int SEARCH_BUDGET_PER_TICK = 3000;
    private static final int MAX_NODES = 60000;
    private static final int STUCK_TICKS = 60;
    private static final int MAX_REPLANS = 8;
    /** A second press of jump within this many ticks of the first turns creative flight on or off. */
    private static final int DOUBLE_TAP_TICKS = 7;

    private final int session;
    private final Supplier<Vec3> target;
    private final double near;
    private PathSearch search;
    private List<PathSearch.Node> path;
    private int index;
    private int ticks;
    private int replans;
    private double bestDistance = Double.MAX_VALUE;
    private int sinceProgress;
    private int lastIndex;
    private BlockPos plannedFor;
    private boolean jumpHeld;
    private int sincePress = DOUBLE_TAP_TICKS;

    public Navigator(int session, Supplier<Vec3> target, double near) {
        this.session = session;
        this.target = target;
        this.near = near;
    }

    private static Minecraft minecraft() {
        return Minecraft.getInstance();
    }

    @Override
    public Result tick() throws CommandException {
        LocalPlayer player = minecraft().player;
        if (player == null) {
            throw new CommandException("not_in_world");
        }
        ticks++;
        Vec3 goal = target.get();
        if (goal == null) {
            release();
            throw new CommandException("no_entity", "target gone");
        }
        double distance = player.position().distanceTo(goal);
        if (distance <= Math.max(near, 0.6)) {
            release();
            return Result.ok(new Fields().bare("took", new Duration(ticks).seconds() + "s"));
        }
        BlockPos goalBlock = BlockPos.containing(goal);
        if (search == null || plannedFor != null && plannedFor.distSqr(goalBlock) > 4 && path != null) {
            plan(player, goalBlock);
        }
        if (path == null) {
            PathSearch.State state = search.step(SEARCH_BUDGET_PER_TICK);
            if (state == PathSearch.State.SEARCHING) {
                return null;
            }
            if (state == PathSearch.State.NO_PATH) {
                release();
                throw new CommandException("no_path", search.expanded() + " cells searched");
            }
            path = search.path();
            index = 0;
            lastIndex = 0;
        }
        // Progress is getting closer or moving along the path; detours around obstacles first lead away.
        if (distance < bestDistance - 0.1 || index > lastIndex) {
            bestDistance = Math.min(bestDistance, distance);
            lastIndex = index;
            sinceProgress = 0;
        } else if (++sinceProgress > STUCK_TICKS) {
            release();
            throw new CommandException("stuck", player.blockPosition().toShortString().replace(", ", ","));
        }
        follow(player, goal);
        return null;
    }

    private void plan(LocalPlayer player, BlockPos goal) {
        BlockPos feet = player.blockPosition();
        search = new PathSearch(new WorldTerrain(player.level()), new PathSearch.Node(feet.getX(), feet.getY(),
            feet.getZ()), goal.getX(), goal.getY(), goal.getZ(), Math.max(near, 0), MAX_NODES);
        plannedFor = goal;
        path = null;
    }

    private void follow(LocalPlayer player, Vec3 goal) {
        if (index >= path.size()) {
            steer(player, goal, goal.y > player.getY() + 0.5);
            return;
        }
        PathSearch.Node node = path.get(index);
        Vec3 center = new Vec3(node.x() + 0.5, node.y(), node.z() + 0.5);
        double horizontal = Math.hypot(center.x - player.getX(), center.z - player.getZ());
        if (horizontal < 0.35 && Math.abs(player.getY() - node.y()) < 1.2) {
            index++;
            follow(player, goal);
            return;
        }
        if (horizontal > 3 || Math.abs(player.getY() - node.y()) > 4) {
            if (replans++ < MAX_REPLANS) {
                plan(player, BlockPos.containing(goal));
            }
            return;
        }
        steer(player, center, node.y() > Math.floor(player.getY() + 0.01) || player.isInWater() && node.y() >= player.getY());
    }

    private void steer(LocalPlayer player, Vec3 toward, boolean jump) {
        double dx = toward.x - player.getX();
        double dz = toward.z - player.getZ();
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f;
        player.setYRot(yaw);
        player.setYHeadRot(yaw);
        var options = minecraft().options;
        InputState.hold(session, options.keyUp);
        sincePress++;
        boolean wanted = jump && (player.onGround() || player.isInWater() || player.onClimbable());
        if (wanted && (jumpHeld || sincePress > DOUBLE_TAP_TICKS || !player.getAbilities().mayfly)) {
            if (!jumpHeld) {
                sincePress = 0;
            }
            jumpHeld = true;
            InputState.hold(session, options.keyJump);
        } else {
            jumpHeld = false;
            InputState.release(options.keyJump);
        }
    }

    private void release() {
        var options = minecraft().options;
        InputState.release(options.keyUp);
        InputState.release(options.keyJump);
        jumpHeld = false;
    }

    @Override
    public void cancel() {
        release();
    }

    @Override
    public String label() {
        return "goto";
    }
}

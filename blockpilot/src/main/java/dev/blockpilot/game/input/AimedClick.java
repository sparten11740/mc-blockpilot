package dev.blockpilot.game.input;

import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A press that waits for the crosshair to rest on a block, for blocks that move (aboard a sub-level). The game works
 * out what the crosshair is on early in its tick and handles key clicks later in the same tick. The player is turned
 * towards the block just before the first, from where it is at that moment, and the hit is checked just before the
 * second, so the press goes to exactly the hit that was checked. Client thread only.
 */
public final class AimedClick {
    private final BlockPos target;
    private final Supplier<Vec3> aim;
    private final Runnable press;
    private boolean fired;

    private static AimedClick armed;

    private AimedClick(BlockPos target, Supplier<Vec3> aim, Runnable press) {
        this.target = target;
        this.aim = aim;
        this.press = press;
    }

    /**
     * During the next tick, turns towards {@code aim} and presses if the crosshair then rests on the target; replaces
     * any other.
     */
    public static AimedClick arm(BlockPos target, Supplier<Vec3> aim, Runnable press) {
        armed = new AimedClick(target, aim, press);
        return armed;
    }

    public boolean fired() {
        return fired;
    }

    public void disarm() {
        if (armed == this) {
            armed = null;
        }
    }

    /** Called by the game just before it works out what the crosshair is on. */
    public static void beforePick(Minecraft minecraft) {
        AimedClick click = armed;
        Vec3 point = click == null ? null : click.aim.get();
        if (point != null && minecraft.player != null) {
            minecraft.player.lookAt(EntityAnchorArgument.Anchor.EYES, point);
        }
    }

    /** Called by the game between working out the crosshair's hit and handling key clicks. */
    public static void beforeKeyHandling(Minecraft minecraft) {
        AimedClick click = armed;
        if (click == null) {
            return;
        }
        armed = null;
        if (minecraft.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK
            && hit.getBlockPos().equals(click.target)) {
            click.fired = true;
            click.press.run();
        }
    }
}

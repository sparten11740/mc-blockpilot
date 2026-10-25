package dev.blockpilot.game.observe;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** A placed camera (an outside view for screenshots) and the camera's angles as last rendered. */
public final class CameraControl {
    /** A point for the frame being rendered; null when it is gone (a removed entity). */
    @FunctionalInterface
    public interface Point {
        Vec3 at(float partialTick);
    }

    /** Where the camera sits and what it looks at; either may move (an entity, a block aboard a sub-level). */
    public record Placement(Point position, Point target) {
        /** Yaw and pitch from the position toward the target. */
        public float[] angles(float partialTick) {
            Vec3 toward = target.at(partialTick);
            if (toward == null) {
                return new float[] {0, 0};
            }
            Vec3 delta = toward.subtract(position.at(partialTick));
            double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
            float yaw = (float) (Mth.atan2(delta.z, delta.x) * Mth.RAD_TO_DEG) - 90f;
            float pitch = (float) -(Mth.atan2(delta.y, horizontal) * Mth.RAD_TO_DEG);
            return new float[] {yaw, pitch};
        }
    }

    /** The camera's rotation as the game last rendered it, including mod camera shake and roll. */
    public record Angles(float yaw, float pitch, float roll) {
    }

    private static volatile Placement placement;
    private static volatile Angles rendered;
    /** The player's own F1 (hide GUI) setting while a placed camera hides the GUI, else null. */
    private static Boolean hiddenGuiBefore;

    private CameraControl() {
    }

    public static Placement placement() {
        return placement;
    }

    /** Places the camera and hides the GUI and hand, as F1 does, so outside views are clean. Client thread. */
    public static void place(Placement value) {
        var options = Minecraft.getInstance().options;
        if (hiddenGuiBefore == null) {
            hiddenGuiBefore = options.hideGui;
        }
        options.hideGui = true;
        placement = value;
        // Until the next frame records them, report the placed camera's own angles, not the previous view's.
        float[] angles = value.angles(1);
        rendered = new Angles(angles[0], angles[1], 0);
    }

    /** Back to the player's view, restoring the GUI setting. Client thread. */
    public static void reset() {
        placement = null;
        rendered = null;
        if (hiddenGuiBefore != null) {
            Minecraft.getInstance().options.hideGui = hiddenGuiBefore;
            hiddenGuiBefore = null;
        }
    }

    public static Angles rendered() {
        return rendered;
    }

    /** Called once per frame after the level rendered, when the camera holds its final angles. */
    public static void recordRendered() {
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        //? if neoforge {
        float roll = camera.getRoll();
        //?} else {
        /*float roll = 0;
        *///?}
        rendered = new Angles(camera.getYRot(), camera.getXRot(), roll);
    }
}

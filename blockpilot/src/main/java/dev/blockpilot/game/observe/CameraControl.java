package dev.blockpilot.game.observe;

import dev.blockpilot.api.Fields;
import dev.blockpilot.api.Position;
import dev.blockpilot.game.sable.Rel;
import dev.blockpilot.game.sable.SableSupport;
import dev.blockpilot.platform.ClientPlatform;
import dev.blockpilot.protocol.CommandFailure;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** A placed camera (an outside view for screenshots) and the camera's angles as last rendered. */
public final class CameraControl {
    /** A point for the frame being rendered; null when it is gone (a removed entity). */
    @FunctionalInterface
    public interface Point {
        Vec3 at(float partialTick);
    }

    /** The way the camera looks for the frame being rendered, from where it sits then; null when it can't tell. */
    @FunctionalInterface
    public interface View {
        Vec3 direction(float partialTick, Vec3 from);
    }

    /**
     * Where the camera sits and what it looks at; either may move (an entity, a block aboard a sub-level). With a
     * view, the camera looks the way it says instead, as during a move or after one that looked ahead.
     */
    public record Placement(Point position, Point target, View view) {
        public Placement(Point position, Point target) {
            this(position, target, null);
        }

        /** The view direction from a point: the view's, or toward the target; null when the target is gone. */
        public Vec3 direction(float partialTick, Vec3 from) {
            if (view != null) {
                return view.direction(partialTick, from);
            }
            Vec3 toward = target.at(partialTick);
            return toward == null ? null : toward.subtract(from);
        }

        /** Yaw and pitch from the position along the view direction. */
        public float[] angles(float partialTick) {
            Vec3 delta = direction(partialTick, position.at(partialTick));
            if (delta == null) {
                return new float[] {0, 0};
            }
            double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
            float yaw = (float) (Mth.atan2(delta.z, delta.x) * Mth.RAD_TO_DEG) - 90f;
            float pitch = (float) -(Mth.atan2(delta.y, horizontal) * Mth.RAD_TO_DEG);
            return new float[] {yaw, pitch};
        }
    }

    /** The unit direction a yaw and pitch look along, as the game turns them. */
    public static Vec3 direction(float yaw, float pitch) {
        double yawRadians = Math.toRadians(yaw);
        double pitchRadians = Math.toRadians(pitch);
        return new Vec3(-Math.sin(yawRadians) * Math.cos(pitchRadians), -Math.sin(pitchRadians),
            Math.cos(yawRadians) * Math.cos(pitchRadians));
    }

    /**
     * The fraction of the current client tick the frame is drawn at, from the game's timer as it runs whether or not
     * the world's ticks are frozen, so a camera move goes on through {@code tick freeze}.
     */
    static float realPartialTick() {
        //? if >=26.1 {
        /*return Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);
        *///?} else {
        return Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
        //?}
    }

    /** A camera fixed where the last frame drew it, looking as it did then; null without a frame from a placed one. */
    static Placement fixedAtLastFrame() {
        Vec3 from = renderedFrom;
        Angles angles = rendered;
        if (from == null || angles == null) {
            return null;
        }
        Vec3 direction = direction(angles.yaw(), angles.pitch());
        return new Placement(partialTick -> from, null, (partialTick, at) -> direction);
    }

    /** The point as it is, or for a point in a sub-level's plot, where the client draws it in each frame. */
    static Point drawn(Level level, Vec3 point) {
        if (SableSupport.subLevels().at(level, BlockPos.containing(point)) == null) {
            return partialTick -> point;
        }
        Vec3[] last = {point};
        return partialTick -> {
            try {
                last[0] = SableSupport.subLevels().toWorldRendered(level, point, partialTick);
            } catch (CommandFailure e) {
                // Thrown on the render thread, where it must not escape; the camera stays where it was last drawn.
            }
            return last[0];
        };
    }

    /** The camera's rotation as the game last rendered it, including mod camera shake and roll. */
    public record Angles(float yaw, float pitch, float roll) {
    }

    private static volatile Placement placement;
    /** Client ticks counted for camera moves, which advance by them whether or not an action ticks them. */
    private static volatile long ticks;
    /** The camera move under way, in the foreground or the background; forgotten when the camera is placed anew. */
    private static volatile CameraMove moving;
    private static volatile Angles rendered;
    private static volatile Vec3 renderedFrom;
    /** The player's own F1 (hide GUI) setting while a placed camera hides the GUI, else null. */
    private static Boolean hiddenGuiBefore;

    private CameraControl() {
    }

    public static Placement placement() {
        return placement;
    }

    /**
     * Each client tick, before the scripts: counts it for camera moves, and places a move in the background at its
     * end once its time is up, as camera at would.
     */
    public static void tick() {
        ticks++;
        CameraMove move = moving;
        if (move != null && move.background() && move.finished()) {
            place(move.end());
        }
    }

    static long ticks() {
        return ticks;
    }

    /** The camera move under way, or null. */
    public static CameraMove moving() {
        return moving;
    }

    /** Places a move, whose placement follows it frame by frame. Client thread. */
    static void placeMove(CameraMove move) {
        place(move.placement());
        moving = move;
    }

    /** stop and real input: a move under way stays where the last frame drew it, on any thread. */
    public static void stopMove() {
        CameraMove move = moving;
        if (move != null) {
            move.cancel();
        }
    }

    /** Places the camera and hides the GUI and hand, as F1 does, so outside views are clean. Client thread. */
    public static void place(Placement value) {
        moving = null;
        if (hiddenGuiBefore == null) {
            hiddenGuiBefore = HudVisibility.hidden();
        }
        HudVisibility.setHidden(true);
        placement = value;
        // Until the next frame records them, report the placed camera's own angles, not the previous view's.
        float[] angles = value.angles(1);
        rendered = new Angles(angles[0], angles[1], 0);
    }

    /**
     * Swaps a placed camera's placement for another, on any thread: the GUI is already hidden, so only the volatile
     * placement changes, as when {@code stop} ends a move from another session's thread.
     */
    static void hold(Placement value) {
        moving = null;
        if (placement != null) {
            placement = value;
        }
    }

    /** Back to the player's view, restoring the GUI setting. Client thread. */
    public static void reset() {
        AwayCamera.end();
        moving = null;
        placement = null;
        rendered = null;
        if (hiddenGuiBefore != null) {
            HudVisibility.setHidden(hiddenGuiBefore);
            hiddenGuiBefore = null;
        }
    }

    public static Angles rendered() {
        return rendered;
    }

    /**
     * Where a placed camera sat for the last rendered frame, as an absolute position, or null when that frame showed
     * the player's view. It follows the frames, not the placement, so right after {@code camera reset} it still names
     * the camera. Menus outside a world don't record frames, and none of them is a placed camera's.
     */
    public static Position frameCamera() {
        Vec3 from = renderedFrom;
        return from == null || Minecraft.getInstance().level == null ? null : Rel.absolute(from);
    }

    /** Adds {@code camera <x,y,z>} for a frame seen from a placed camera. */
    static Fields withFrameCamera(Fields fields, Position camera) {
        return camera == null ? fields : fields.put("camera", camera, "camera " + camera);
    }

    /** Called once per frame after the level rendered, when the camera holds its final angles and position. */
    public static void recordRendered() {
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        rendered = new Angles(camera.getYRot(), camera.getXRot(), ClientPlatform.cameraRoll(camera));
        renderedFrom = placement == null ? null : camera.getPosition();
    }
}

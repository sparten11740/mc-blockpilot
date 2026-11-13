package dev.blockpilot.game.observe;

import dev.blockpilot.api.Fields;
import dev.blockpilot.api.Position;
import dev.blockpilot.game.sable.Rel;
import dev.blockpilot.game.sable.SableSupport;
import dev.blockpilot.platform.ClientPlatform;
import dev.blockpilot.protocol.CommandFailure;
import java.util.function.Supplier;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

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

    /** A placed camera's own vertical field of view, null for the player's, and its roll, in degrees. */
    public record Lens(Double fov, double roll) {
        public static final Lens PLAYER = new Lens(null, 0);

        /** This lens with the values given, null keeping its own. */
        Lens with(Double newFov, Double newRoll) {
            return new Lens(newFov != null ? newFov : fov, newRoll != null ? newRoll : roll);
        }
    }

    /**
     * Where the camera sits and what it looks at; either may move (an entity, a block aboard a sub-level). With a
     * view, the camera looks the way it says instead, as during a move or after one that looked ahead. The lens gives
     * the field of view and roll for the frame; placed without one, the camera keeps the lens it had.
     */
    public record Placement(Point position, Point target, View view, Supplier<Lens> lens) {
        public Placement(Point position, Point target) {
            this(position, target, null, null);
        }

        public Placement(Point position, Point target, View view) {
            this(position, target, view, null);
        }

        Placement withLens(Supplier<Lens> value) {
            return new Placement(position, target, view, value);
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
        //? if >=1.21.11 {
        /*return Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);
        *///?} else if >=1.21 {
        return Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
        //?} else {
        /*return Minecraft.getInstance().getFrameTime();
        *///?}
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

    /** The point, or once it is gone (a removed entity) where it last was, at first {@code initial}; never null. */
    static Point held(Point point, Vec3 initial) {
        Vec3[] last = {initial};
        return partialTick -> {
            Vec3 at = point.at(partialTick);
            if (at != null) {
                last[0] = at;
            }
            return last[0];
        };
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
    /** The timed motion under way, in the foreground or the background; forgotten when the camera is placed anew. */
    private static volatile CameraMotion moving;
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
        CameraMotion motion = moving;
        if (motion != null && motion.background() && motion.finished()) {
            place(motion.end());
        }
    }

    static long ticks() {
        return ticks;
    }

    /** The camera move or other timed motion under way, or null. */
    public static CameraMotion moving() {
        return moving;
    }

    /** Places a timed motion, whose placement follows it frame by frame. Client thread. */
    static void placeMove(CameraMotion motion) {
        place(motion.placement());
        moving = motion;
    }

    /** stop and real input: a timed motion under way stays where the last frame drew it, on any thread. */
    public static void stopMove() {
        CameraMotion motion = moving;
        if (motion != null) {
            motion.cancel();
        }
    }

    /** Places the camera and hides the GUI and hand, as F1 does, so outside views are clean. Client thread. */
    public static void place(Placement value) {
        moving = null;
        if (hiddenGuiBefore == null) {
            hiddenGuiBefore = HudVisibility.hidden();
        }
        HudVisibility.setHidden(true);
        placement = keepingLens(value);
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
            placement = keepingLens(value);
        }
    }

    /** The placement, with the lens in use now when it brings none. */
    private static Placement keepingLens(Placement value) {
        if (value.lens() != null) {
            return value;
        }
        Lens now = lens();
        return value.withLens(() -> now);
    }

    /** The lens for the frame being rendered: a placed camera's, else the player's. */
    public static Lens lens() {
        Placement current = placement;
        return current == null ? Lens.PLAYER : current.lens().get();
    }

    /** A placed camera's own field of view for the world, or null for the player's. */
    public static Double placedFov() {
        return placement == null ? null : lens().fov();
    }

    /**
     * Tilts a placed camera's rotation, just set from its yaw and pitch, about the way it looks by its lens's roll,
     * so the view matrix, particles and frustum all see it.
     */
    public static void roll(Quaternionf rotation) {
        double roll = placedRoll();
        if (roll != 0) {
            rotation.rotateZ((float) Math.toRadians(-roll));
        }
    }

    /** A placed camera's roll in degrees, 0 for none. */
    public static double placedRoll() {
        return placement == null ? 0 : lens().roll();
    }

    /** The lens in use with the values given, null keeping its own, for a camera placed at once; null for none. */
    static Supplier<Lens> lensWith(Double fov, Double roll) {
        if (fov == null && roll == null) {
            return null;
        }
        Lens set = lens().with(fov, roll);
        return () -> set;
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
        rendered = new Angles(camera.getYRot(), camera.getXRot(), ClientPlatform.cameraRoll(camera)
            + (float) lens().roll());
        renderedFrom = placement == null ? null : camera.getPosition();
    }
}

package dev.blockpilot.game.observe;

import dev.blockpilot.api.Action;
import dev.blockpilot.game.observe.CameraPath.Vec;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.minecraft.world.phys.Vec3;

/**
 * {@code camera move}: glides the camera along a path. Its placement works out the move's progress in every rendered
 * frame and builds the path from where its points are drawn then, so the camera is in a new place each frame and
 * follows points aboard a moving sub-level.
 */
final class CameraMove extends CameraMotion {
    /** Where the move looks: on as before, toward a target, or the way it travels. */
    enum Look { KEEP, TARGET, AHEAD }

    /** Over the first fifth of a move, {@code look ahead} turns from the old direction onto the path. */
    private static final double LEAD_IN = 0.2;

    private final List<CameraControl.Point> points;
    private final Look look;
    private final CameraControl.Point target;
    /** The placement the move set off from, or null from the player's view. */
    private final CameraControl.Placement before;
    /** From the player's view, the way the player looked when the move began. */
    private final Vec3 startDirection;

    /** {@code points}: the start, the via points and the end. */
    CameraMove(List<CameraControl.Point> points, Look look, CameraControl.Point target,
        CameraControl.Placement before, Vec3 startDirection, int duration, CameraPath.Ease ease, boolean background,
        Function<CameraControl.Placement, Action> answer) {
        super(duration, ease, background, answer);
        this.points = List.copyOf(points);
        this.look = look;
        this.target = target;
        this.before = before;
        this.startDirection = startDirection;
    }

    @Override
    CameraControl.Placement end() {
        return new CameraControl.Placement(points.get(points.size() - 1), finalTarget(), finalView());
    }

    @Override
    CameraControl.Placement placement() {
        return new CameraControl.Placement(partialTick -> {
            Frame frame = frame(partialTick);
            return toVec3(frame.path.at(frame.eased));
        }, null, this::direction);
    }

    @Override
    CameraControl.Placement stillNow() {
        Frame now = frame(1);
        Vec3 at = toVec3(now.path.at(now.eased));
        Vec3 direction = direction(1, at);
        return new CameraControl.Placement(partialTick -> at, null, (partialTick, from) -> direction);
    }

    /** The path for a frame, from where its points are drawn at that partial tick, and how far along it to be. */
    private record Frame(CameraPath path, double progress, double eased) {
    }

    private Frame frame(float partialTick) {
        List<Vec> through = new ArrayList<>();
        for (CameraControl.Point point : points) {
            Vec3 at = point.at(partialTick);
            if (at != null) {
                through.add(toVec(at));
            }
        }
        double progress = progress();
        return new Frame(new CameraPath(through), progress, eased(progress));
    }

    private Vec3 direction(float partialTick, Vec3 from) {
        Vec3 old = oldDirection(partialTick, from);
        return switch (look) {
            case KEEP -> old;
            case TARGET -> {
                Vec3 toward = target.at(partialTick);
                yield toward == null || old == null ? old
                    : toVec3(CameraPath.turn(toVec(old), toVec(toward.subtract(from)), frame(partialTick).eased));
            }
            case AHEAD -> {
                Frame frame = frame(partialTick);
                Vec tangent = frame.path.tangent(frame.eased);
                if (tangent == null || old == null) {
                    yield old;
                }
                double lead = Math.min(1, frame.progress / LEAD_IN);
                yield toVec3(CameraPath.turn(toVec(old), tangent, lead * lead * (3 - 2 * lead)));
            }
        };
    }

    private Vec3 oldDirection(float partialTick, Vec3 from) {
        return before != null ? before.direction(partialTick, from) : startDirection;
    }

    private CameraControl.Point finalTarget() {
        return look == Look.TARGET ? target : look == Look.KEEP && before != null ? before.target() : null;
    }

    /** After the move: the old view kept, the last tangent for look ahead, or none toward a target. */
    private CameraControl.View finalView() {
        return switch (look) {
            case TARGET -> null;
            case KEEP -> before != null ? before.view() : (partialTick, from) -> startDirection;
            case AHEAD -> {
                Frame last = frame(1);
                Vec tangent = last.path.tangent(1);
                if (tangent == null) {
                    yield before != null ? before::direction : (partialTick, from) -> startDirection;
                }
                Vec3 direction = toVec3(tangent);
                yield (partialTick, from) -> direction;
            }
        };
    }

    private static Vec toVec(Vec3 point) {
        return new Vec(point.x, point.y, point.z);
    }

    private static Vec3 toVec3(Vec point) {
        return point == null ? null : new Vec3(point.x(), point.y(), point.z());
    }
}

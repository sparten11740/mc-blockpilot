package dev.blockpilot.game.observe;

import dev.blockpilot.api.Action;
import dev.blockpilot.game.observe.CameraPath.Vec;
import java.util.function.Function;
import net.minecraft.world.phys.Vec3;

/**
 * {@code camera orbit}: circles the camera round a centre, a point or an entity read where each frame draws it,
 * keeping the offset it had from the centre at the start turned about the vertical by the eased share of the angle,
 * and looking at the centre. A centre that is gone leaves the camera where it was last drawn.
 */
final class CameraOrbit extends CameraMotion {
    /** The centre for a frame, null once it is gone (a removed entity). */
    private final CameraControl.Point centre;
    private final CameraControl.Point target;
    private final Vec offset;
    private final double degrees;
    private final CameraControl.Point position;

    /** {@code from}: where the camera is drawn as the orbit starts; {@code at}: the centre then. */
    CameraOrbit(CameraControl.Point centre, Vec3 from, Vec3 at, double degrees, int duration, CameraPath.Ease ease,
        boolean background, Double fov, Double roll, Function<CameraControl.Placement, Action> answer) {
        super(duration, ease, background, fov, roll, answer);
        this.centre = centre;
        offset = new Vec(from.x - at.x, from.y - at.y, from.z - at.z);
        this.degrees = degrees;
        target = CameraControl.held(centre, at);
        position = CameraControl.held(partialTick -> around(centre.at(partialTick), eased(progress())), from);
    }

    @Override
    CameraControl.Placement movingShape() {
        return new CameraControl.Placement(position, target);
    }

    @Override
    CameraControl.Placement endShape() {
        Vec3 last = around(centre.at(1), 1);
        Vec3 rest = last != null ? last : position.at(1);
        return new CameraControl.Placement(partialTick -> rest, target);
    }

    @Override
    CameraControl.Placement stillShape() {
        Vec3 now = position.at(1);
        Vec3 direction = target.at(1).subtract(now);
        return new CameraControl.Placement(partialTick -> now, null, (partialTick, from) -> direction);
    }

    /** Where the camera is with that share of the angle turned, round the centre; null without a centre. */
    private Vec3 around(Vec3 at, double share) {
        if (at == null) {
            return null;
        }
        Vec turned = CameraPath.aroundVertical(offset, degrees * share);
        return at.add(turned.x(), turned.y(), turned.z());
    }
}

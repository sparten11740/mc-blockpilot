package dev.blockpilot.game.observe;

import dev.blockpilot.game.observe.CameraPath.Vec;
import net.minecraft.world.entity.Entity;
//? if <1.21 {
/*import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
*///?}
import net.minecraft.world.phys.Vec3;

/** {@code camera follow}: the camera kept at an offset from an entity, read where each frame draws it. */
final class CameraFollow {
    /** How far a turning follow's heading lags its entity's, as a time constant in client ticks. */
    private static final double LAG_TICKS = 10;

    private CameraFollow() {
    }

    /**
     * The placement at the offset from the entity's feet, the world's or with {@code turning} one in the entity's
     * frame, looking at the target. Once the entity is removed the camera stays where it was last drawn, looking at
     * where the target last was.
     */
    static CameraControl.Placement placement(Entity entity, Vec offset, boolean turning, CameraControl.Point target) {
        CameraPath.Heading heading = new CameraPath.Heading(LAG_TICKS);
        Vec now = turning ? CameraPath.aroundVertical(offset, -bodyRotation(entity, 1)) : offset;
        CameraControl.Point position = CameraControl.held(partialTick -> {
            if (entity.isRemoved()) {
                return null;
            }
            Vec world = turning ? CameraPath.aroundVertical(offset, -heading.at(CameraControl.ticks()
                + CameraControl.realPartialTick(), bodyRotation(entity, partialTick))) : offset;
            return entity.getPosition(partialTick).add(world.x(), world.y(), world.z());
        }, entity.position().add(now.x(), now.y(), now.z()));
        return new CameraControl.Placement(position, CameraControl.held(target, target.at(1)));
    }

    /** The placement at an offset in the world's axes from a moving point, such as a sub-level's origin, looking at it. */
    static CameraControl.Placement placement(CameraControl.Point origin, Vec offset) {
        Vec3 start = origin.at(1);
        CameraControl.Point position = CameraControl.held(partialTick -> {
            Vec3 at = origin.at(partialTick);
            return at == null ? null : at.add(offset.x(), offset.y(), offset.z());
        }, start.add(offset.x(), offset.y(), offset.z()));
        return new CameraControl.Placement(position, CameraControl.held(origin, start));
    }

    /** The offset a point has from the entity's feet: the world's, or with {@code turning} in the entity's frame. */
    static Vec offsetOf(Entity entity, Vec3 point, boolean turning) {
        Vec3 delta = point.subtract(entity.position());
        Vec world = new Vec(delta.x, delta.y, delta.z);
        return turning ? CameraPath.aroundVertical(world, bodyRotation(entity, 1)) : world;
    }

    /** The way the entity's body faces, between its last two ticks. */
    private static float bodyRotation(Entity entity, float partialTick) {
        //? if >=1.21 {
        return entity.getPreciseBodyRotation(partialTick);
        //?} else {
        /*return entity instanceof LivingEntity living ? Mth.lerp(partialTick, living.yBodyRotO, living.yBodyRot)
            : Mth.lerp(partialTick, entity.yRotO, entity.getYRot());
        *///?}
    }
}

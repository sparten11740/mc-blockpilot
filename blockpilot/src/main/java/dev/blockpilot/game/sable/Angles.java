package dev.blockpilot.game.sable;

import org.joml.Quaterniondc;
import org.joml.Vector3d;

/**
 * A rotation as Minecraft's yaw, pitch and roll in degrees, turned in that order: yaw 90 faces west, pitch 30 points
 * down, and roll is positive when the right side goes down.
 */
public record Angles(float yaw, float pitch, float roll) {
    public static Angles of(Quaterniondc rotation) {
        Vector3d euler = rotation.getEulerAnglesYXZ(new Vector3d());
        return new Angles(degrees(-euler.y), degrees(euler.x), degrees(euler.z));
    }

    /** Adding 0 turns -0 into 0, which prints as {@code 0.0}. */
    private static float degrees(double radians) {
        return (float) Math.toDegrees(radians) + 0.0f;
    }
}

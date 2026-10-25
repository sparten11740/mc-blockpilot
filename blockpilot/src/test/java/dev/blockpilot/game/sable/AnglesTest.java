package dev.blockpilot.game.sable;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.joml.Quaterniond;
import org.junit.jupiter.api.Test;

class AnglesTest {
    private static final float EPSILON = 1e-4f;

    private static void assertAngles(float yaw, float pitch, float roll, Angles angles) {
        assertEquals(yaw, angles.yaw(), EPSILON, "yaw");
        assertEquals(pitch, angles.pitch(), EPSILON, "pitch");
        assertEquals(roll, angles.roll(), EPSILON, "roll");
    }

    @Test
    void noRotationIsAllZero() {
        assertEquals(new Angles(0, 0, 0), Angles.of(new Quaterniond()));
    }

    @Test
    void turningFromSouthTowardsWestIsPositiveYaw() {
        assertAngles(90, 0, 0, Angles.of(new Quaterniond().rotateY(Math.toRadians(-90))));
    }

    @Test
    void noseDownIsPositivePitch() {
        assertAngles(0, 30, 0, Angles.of(new Quaterniond().rotateX(Math.toRadians(30))));
    }

    @Test
    void anglesComeOutInTheOrderTheyWereTurned() {
        Quaterniond rotation = new Quaterniond().rotateY(Math.toRadians(-45)).rotateX(Math.toRadians(10))
            .rotateZ(Math.toRadians(20));
        assertAngles(45, 10, 20, Angles.of(rotation));
    }
}

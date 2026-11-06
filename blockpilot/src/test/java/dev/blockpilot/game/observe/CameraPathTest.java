package dev.blockpilot.game.observe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.blockpilot.game.observe.CameraPath.Ease;
import dev.blockpilot.game.observe.CameraPath.Vec;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CameraPathTest {
    private static double degrees(Vec a, Vec b) {
        return Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, a.unit().dot(b.unit())))));
    }

    @Test
    void eachEasingRunsFromZeroToOne() {
        double[] halfway = {0.5, 0.25, 0.75, 0.5};
        for (Ease ease : Ease.values()) {
            assertEquals(0, ease.apply(0), 1e-12, ease.name());
            assertEquals(halfway[ease.ordinal()], ease.apply(0.5), 1e-12, ease.name());
            assertEquals(1, ease.apply(1), 1e-12, ease.name());
        }
    }

    @Test
    void thePathPassesThroughEveryPoint() {
        List<Vec> points = List.of(new Vec(0, 0, 0), new Vec(5, 1, 2), new Vec(6, 3, 9), new Vec(-2, 2, 12));
        CameraPath path = new CameraPath(points);
        for (int point = 0; point < points.size(); point++) {
            Vec at = path.at(path.distanceAt(point) / path.length());
            assertTrue(at.minus(points.get(point)).length() < 1e-6, "point " + point + " at " + at);
        }
    }

    @Test
    void equalStepsOfProgressCoverEqualDistancesWhateverTheSpacing() {
        // The second span is ten times the first's length.
        CameraPath path = new CameraPath(List.of(new Vec(0, 0, 0), new Vec(2, 0, 0), new Vec(12, 0, 20),
            new Vec(14, 0, 20)));
        int steps = 100;
        List<Double> gaps = new ArrayList<>();
        Vec last = path.at(0);
        for (int step = 1; step <= steps; step++) {
            Vec next = path.at(step / (double) steps);
            gaps.add(next.minus(last).length());
            last = next;
        }
        double mean = gaps.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
        double worst = gaps.stream().mapToDouble(gap -> Math.abs(gap - mean) / mean).max().orElseThrow();
        assertTrue(worst < 0.02, "a step " + Math.round(worst * 1000) / 10.0 + " % off the mean");
    }

    @Test
    void theDirectionDoesntJumpAtAViaPoint() {
        CameraPath path = new CameraPath(List.of(new Vec(0, 0, 0), new Vec(10, 0, 0), new Vec(10, 0, 10)));
        double via = path.distanceAt(1) / path.length();
        double before = degrees(path.tangent(via - 1e-4), path.tangent(via + 1e-4));
        assertTrue(before < 1, before + " degrees across the via point");
    }

    @Test
    void twoPointsGiveTheStraightLine() {
        CameraPath path = new CameraPath(List.of(new Vec(0, 0, 0), new Vec(10, 4, 0)));
        assertTrue(path.at(0.5).minus(new Vec(5, 2, 0)).length() < 1e-9, "halfway is " + path.at(0.5));
        for (int step = 0; step <= 10; step++) {
            Vec at = path.at(step / 10.0);
            assertEquals(0, at.cross(new Vec(10, 4, 0)).length(), 1e-9);
        }
        assertTrue(degrees(path.tangent(0.3), new Vec(10, 4, 0)) < 1e-6);
    }

    @Test
    void nearlyRepeatedPointsAreDropped() {
        CameraPath path = new CameraPath(List.of(new Vec(0, 0, 0), new Vec(0.001, 0, 0), new Vec(4, 0, 0)));
        assertEquals(4, path.length(), 1e-9);
    }

    @Test
    void theViewTurnsAlongTheShortWay() {
        Vec east = new Vec(1, 0, 0);
        Vec south = new Vec(0, 0, 1);
        Vec halfway = CameraPath.turn(east, south, 0.5);
        assertTrue(degrees(halfway, new Vec(1, 0, 1)) < 1e-6, "south-east, not " + halfway);
        Vec opposite = CameraPath.turn(east, new Vec(-1, 0, 0), 0.5);
        assertEquals(0, opposite.y(), 1e-9, "a turn between opposite directions stays horizontal");
        assertEquals(1, opposite.length(), 1e-9);
        assertEquals(new Vec(-1, 0, 0), CameraPath.turn(east, new Vec(-1, 0, 0), 1));
    }

    private static void assertNear(Vec expected, Vec actual) {
        assertTrue(expected.minus(actual).length() < 1e-9, expected + " but was " + actual);
    }

    @Test
    void aPositiveAngleTurnsACameraToItsRightAsItFacesTheCentre() {
        // From north of the centre, facing south, the camera's right is west.
        assertNear(new Vec(-6, 2, 0), CameraPath.aroundVertical(new Vec(0, 2, -6), 90));
        assertNear(new Vec(6, 2, 0), CameraPath.aroundVertical(new Vec(0, 2, -6), -90));
        assertNear(new Vec(0, 2, 6), CameraPath.aroundVertical(new Vec(0, 2, -6), 180));
        assertNear(new Vec(0, 0, 0), CameraPath.aroundVertical(new Vec(0, 0, 0), 37));
    }

    @Test
    void anOffsetInAnEntitysFrameTurnsWithItsYaw() {
        // Six behind and two above an entity facing west (yaw 90) is six to the east.
        Vec world = CameraPath.aroundVertical(new Vec(0, 2, -6), -90);
        assertNear(new Vec(6, 2, 0), world);
        assertNear(new Vec(0, 2, -6), CameraPath.aroundVertical(world, 90));
        // One to the left of an entity facing south (yaw 0) is one to the east.
        assertNear(new Vec(1, 0, 0), CameraPath.aroundVertical(new Vec(1, 0, 0), 0));
    }

    @Test
    void aHeadingFollowsWithALagAndOnlyWhenTheTimeMoves() {
        CameraPath.Heading heading = new CameraPath.Heading(10);
        assertEquals(0, heading.at(0, 0), 1e-12);
        assertEquals(0, heading.at(0, 90), 1e-12);
        assertEquals(90 * (1 - Math.exp(-1)), heading.at(10, 90), 1e-9);
        assertEquals(90 * (1 - Math.exp(-1)), heading.at(10, 90), 1e-9);
        assertEquals(90, heading.at(1000, 90), 1e-6);
    }

    @Test
    void aHeadingTurnsTheShortWayRound() {
        CameraPath.Heading heading = new CameraPath.Heading(10);
        heading.at(0, 170);
        // From 170 to -170 is 20 degrees on past south-west, not 340 back.
        assertEquals(190, heading.at(1000, -170), 1e-6);
    }
}

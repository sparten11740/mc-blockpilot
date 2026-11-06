package dev.blockpilot.game.observe;

import java.util.ArrayList;
import java.util.List;

/**
 * The maths of a camera move, free of the game: a path through points that is smooth at each of them, walked at a
 * constant speed, the easing of that walk, and the turn of the view. Built anew for each frame from where the points
 * are drawn then.
 */
final class CameraPath {
    record Vec(double x, double y, double z) {
        static final Vec UP = new Vec(0, 1, 0);

        Vec plus(Vec other) {
            return new Vec(x + other.x, y + other.y, z + other.z);
        }

        Vec minus(Vec other) {
            return new Vec(x - other.x, y - other.y, z - other.z);
        }

        Vec times(double factor) {
            return new Vec(x * factor, y * factor, z * factor);
        }

        double dot(Vec other) {
            return x * other.x + y * other.y + z * other.z;
        }

        Vec cross(Vec other) {
            return new Vec(y * other.z - z * other.y, z * other.x - x * other.z, x * other.y - y * other.x);
        }

        double length() {
            return Math.sqrt(dot(this));
        }

        /** This direction with length 1; null when it has none. */
        Vec unit() {
            double length = length();
            return length < 1e-9 ? null : times(1 / length);
        }
    }

    /** How the walk along the path speeds up and slows down; {@code s} is the move's progress, 0 to 1. */
    enum Ease {
        LINEAR, IN, OUT, INOUT;

        double apply(double s) {
            return switch (this) {
                case LINEAR -> s;
                case IN -> s * s;
                case OUT -> 1 - (1 - s) * (1 - s);
                case INOUT -> s * s * (3 - 2 * s);
            };
        }
    }

    /** Samples per span for the arc-length table. */
    private static final int STEPS = 64;
    /** Points closer than this to the one before are dropped, which keeps the knot spacing above zero. */
    private static final double MIN_GAP = 0.01;
    /** Within this angle of opposite, two view directions turn about the vertical rather than over the top. */
    private static final double OPPOSITE = Math.toRadians(179);

    private final List<Vec> points;
    /** The distance along the path at each sample: span {@code s}, step {@code k} at {@code s * STEPS + k}. */
    private final double[] distances;

    /** A path through the points in order: the start, any via points and the end. */
    CameraPath(List<Vec> through) {
        points = new ArrayList<>();
        for (Vec point : through) {
            if (points.isEmpty() || point.minus(points.get(points.size() - 1)).length() >= MIN_GAP) {
                points.add(point);
            }
        }
        int spans = points.size() - 1;
        distances = new double[Math.max(1, spans * STEPS + 1)];
        Vec last = points.get(0);
        for (int sample = 1; sample < distances.length; sample++) {
            Vec point = spanPoint(sample / STEPS - (sample % STEPS == 0 ? 1 : 0), sample % STEPS == 0 ? 1
                : (sample % STEPS) / (double) STEPS);
            distances[sample] = distances[sample - 1] + point.minus(last).length();
            last = point;
        }
    }

    double length() {
        return distances[distances.length - 1];
    }

    /** How far along the path the point it passes through is; 0 for the start. */
    double distanceAt(int point) {
        return distances[Math.min(point, points.size() - 1) * STEPS];
    }

    /** The point the given fraction of the path's length along it. */
    Vec at(double fraction) {
        double[] place = place(fraction);
        return spanPoint((int) place[0], place[1]);
    }

    /** The direction of travel at the given fraction of the length, from the curve's shape; null without length. */
    Vec tangent(double fraction) {
        if (points.size() < 2) {
            return null;
        }
        double[] place = place(fraction);
        return spanDerivative((int) place[0], place[1]).unit();
    }

    /** The span and its parameter, 0 to 1, at a fraction of the length, by the arc-length table. */
    private double[] place(double fraction) {
        if (points.size() < 2) {
            return new double[] {0, 0};
        }
        double target = Math.max(0, Math.min(1, fraction)) * length();
        int low = 0;
        int high = distances.length - 1;
        while (high - low > 1) {
            int middle = (low + high) >>> 1;
            if (distances[middle] <= target) {
                low = middle;
            } else {
                high = middle;
            }
        }
        double gap = distances[high] - distances[low];
        double sample = low + (gap <= 0 ? 0 : (target - distances[low]) / gap);
        int span = Math.min((int) (sample / STEPS), points.size() - 2);
        return new double[] {span, (sample - span * STEPS) / STEPS};
    }

    /** The four points of a span and its centripetal knots: the points either side, mirrored past the ends. */
    private record Span(Vec p0, Vec p1, Vec p2, Vec p3, double t0, double t1, double t2, double t3) {
    }

    private Span span(int index) {
        Vec p1 = points.get(index);
        Vec p2 = points.get(index + 1);
        Vec p0 = index > 0 ? points.get(index - 1) : p1.times(2).minus(p2);
        Vec p3 = index + 2 < points.size() ? points.get(index + 2) : p2.times(2).minus(p1);
        double t1 = Math.sqrt(p1.minus(p0).length());
        double t2 = t1 + Math.sqrt(p2.minus(p1).length());
        double t3 = t2 + Math.sqrt(p3.minus(p2).length());
        return new Span(p0, p1, p2, p3, 0, t1, t2, t3);
    }

    /** Barry and Goldman's pyramid for the Catmull-Rom point at parameter {@code u} of a span. */
    private Vec spanPoint(int index, double u) {
        if (points.size() < 2) {
            return points.get(0);
        }
        if (points.size() == 2) {
            return points.get(0).plus(points.get(1).minus(points.get(0)).times(u));
        }
        Span s = span(index);
        double t = s.t1 + u * (s.t2 - s.t1);
        Vec a1 = lerp(s.p0, s.p1, s.t0, s.t1, t);
        Vec a2 = lerp(s.p1, s.p2, s.t1, s.t2, t);
        Vec a3 = lerp(s.p2, s.p3, s.t2, s.t3, t);
        Vec b1 = lerp(a1, a2, s.t0, s.t2, t);
        Vec b2 = lerp(a2, a3, s.t1, s.t3, t);
        return lerp(b1, b2, s.t1, s.t2, t);
    }

    /** The derivative of the pyramid's point by its parameter: the curve's direction there. */
    private Vec spanDerivative(int index, double u) {
        if (points.size() == 2) {
            return points.get(1).minus(points.get(0));
        }
        Span s = span(index);
        double t = s.t1 + u * (s.t2 - s.t1);
        Vec a1 = lerp(s.p0, s.p1, s.t0, s.t1, t);
        Vec a2 = lerp(s.p1, s.p2, s.t1, s.t2, t);
        Vec a3 = lerp(s.p2, s.p3, s.t2, s.t3, t);
        Vec da1 = s.p1.minus(s.p0).times(1 / (s.t1 - s.t0));
        Vec da2 = s.p2.minus(s.p1).times(1 / (s.t2 - s.t1));
        Vec da3 = s.p3.minus(s.p2).times(1 / (s.t3 - s.t2));
        Vec b1 = lerp(a1, a2, s.t0, s.t2, t);
        Vec b2 = lerp(a2, a3, s.t1, s.t3, t);
        Vec db1 = a2.minus(a1).times(1 / (s.t2 - s.t0)).plus(lerp(da1, da2, s.t0, s.t2, t));
        Vec db2 = a3.minus(a2).times(1 / (s.t3 - s.t1)).plus(lerp(da2, da3, s.t1, s.t3, t));
        return b2.minus(b1).times(1 / (s.t2 - s.t1)).plus(lerp(db1, db2, s.t1, s.t2, t));
    }

    /** The point between {@code a} at knot {@code ta} and {@code b} at {@code tb}, at {@code t}. */
    private static Vec lerp(Vec a, Vec b, double ta, double tb, double t) {
        return a.times((tb - t) / (tb - ta)).plus(b.times((t - ta) / (tb - ta)));
    }

    /**
     * The view direction {@code amount} of the way from one direction to the other, turning along the great circle
     * between them; within a degree of opposite, about the vertical, so the view pans instead of flipping over.
     */
    static Vec turn(Vec from, Vec to, double amount) {
        Vec start = from.unit();
        Vec end = to.unit();
        if (start == null || end == null || amount >= 1) {
            return end != null ? end : start;
        }
        if (amount <= 0) {
            return start;
        }
        double angle = Math.acos(Math.max(-1, Math.min(1, start.dot(end))));
        if (angle < 1e-6) {
            return end;
        }
        if (angle > OPPOSITE) {
            // The axis closest to the vertical that the start direction turns about.
            Vec axis = Vec.UP.minus(start.times(Vec.UP.dot(start))).unit();
            if (axis == null) {
                axis = new Vec(1, 0, 0);
            }
            double turned = angle * amount;
            return start.times(Math.cos(turned)).plus(axis.cross(start).times(Math.sin(turned)));
        }
        double sin = Math.sin(angle);
        return start.times(Math.sin((1 - amount) * angle) / sin).plus(end.times(Math.sin(amount * angle) / sin));
    }
}

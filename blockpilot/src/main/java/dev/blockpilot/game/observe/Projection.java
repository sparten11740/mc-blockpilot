package dev.blockpilot.game.observe;

/**
 * The camera of one rendered frame: its position, its view rotation and its projection (with view bobbing), as
 * column-major 4×4 matrices the way the renderer used them. Free of game classes.
 */
public record Projection(double cameraX, double cameraY, double cameraZ, float[] view, float[] projection) {
    public Projection {
        view = view.clone();
        projection = projection.clone();
    }

    /**
     * The part of the frame around a world point, reaching {@code radius} blocks to each side of it in the view's
     * plane, cut to the frame; null when the point is behind the camera or outside the frame.
     */
    public FrameRect around(double x, double y, double z, double radius) {
        double[] eye = transform(view, x - cameraX, y - cameraY, z - cameraZ);
        double[] centre = toFrame(eye[0], eye[1], eye[2]);
        if (centre == null || centre[0] < 0 || centre[0] > 1 || centre[1] < 0 || centre[1] > 1) {
            return null;
        }
        double[] left = toFrame(eye[0] - radius, eye[1], eye[2]);
        double[] right = toFrame(eye[0] + radius, eye[1], eye[2]);
        double[] top = toFrame(eye[0], eye[1] + radius, eye[2]);
        double[] bottom = toFrame(eye[0], eye[1] - radius, eye[2]);
        if (left == null || right == null || top == null || bottom == null) {
            return null;
        }
        return new FrameRect(clamp(Math.min(left[0], right[0])), clamp(Math.min(top[1], bottom[1])),
            clamp(Math.max(left[0], right[0])), clamp(Math.max(top[1], bottom[1])));
    }

    /** A point in view space as frame fractions from the top left, or null when it is behind the camera. */
    private double[] toFrame(double x, double y, double z) {
        double[] clip = transform(projection, x, y, z);
        if (clip[3] <= 0) {
            return null;
        }
        return new double[] {(clip[0] / clip[3] + 1) / 2, (1 - clip[1] / clip[3]) / 2};
    }

    private static double[] transform(float[] matrix, double x, double y, double z) {
        double[] out = new double[4];
        for (int row = 0; row < 4; row++) {
            out[row] = matrix[row] * x + matrix[4 + row] * y + matrix[8 + row] * z + matrix[12 + row];
        }
        return out;
    }

    private static double clamp(double fraction) {
        return Math.max(0, Math.min(1, fraction));
    }
}

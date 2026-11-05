package dev.blockpilot.game.common;

import java.util.List;

/** A block shape's bounds within its block, each coordinate 0 to 1 for a shape that stays in its block. */
record ShapeBounds(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
    static final ShapeBounds FULL = new ShapeBounds(0, 0, 0, 1, 1, 1);

    /** The bounds enclosing a shape's boxes, or the whole cell for a shape without any. */
    static ShapeBounds enclosing(List<ShapeBounds> boxes) {
        if (boxes.isEmpty()) {
            return FULL;
        }
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        double maxZ = -Double.MAX_VALUE;
        for (ShapeBounds box : boxes) {
            minX = Math.min(minX, box.minX);
            minY = Math.min(minY, box.minY);
            minZ = Math.min(minZ, box.minZ);
            maxX = Math.max(maxX, box.maxX);
            maxY = Math.max(maxY, box.maxY);
            maxZ = Math.max(maxZ, box.maxZ);
        }
        return new ShapeBounds(minX, minY, minZ, maxX, maxY, maxZ);
    }

    /** The centre of the bounds' face toward the neighbour one step away, each step -1, 0 or 1 and one not 0. */
    BlockFrame.Point faceToward(int stepX, int stepY, int stepZ) {
        return new BlockFrame.Point(side(minX, maxX, stepX), side(minY, maxY, stepY), side(minZ, maxZ, stepZ));
    }

    private static double side(double min, double max, int step) {
        return step > 0 ? max : step < 0 ? min : (min + max) / 2;
    }
}

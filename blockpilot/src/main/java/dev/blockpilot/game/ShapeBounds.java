package dev.blockpilot.game;

/** A block shape's bounds within its block, each coordinate 0 to 1 for a shape that stays in its block. */
record ShapeBounds(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
    static final ShapeBounds FULL = new ShapeBounds(0, 0, 0, 1, 1, 1);

    /** The centre of the bounds' face toward the neighbour one step away, each step -1, 0 or 1 and one not 0. */
    BlockFrame.Point faceToward(int stepX, int stepY, int stepZ) {
        return new BlockFrame.Point(side(minX, maxX, stepX), side(minY, maxY, stepY), side(minZ, maxZ, stepZ));
    }

    private static double side(double min, double max, int step) {
        return step > 0 ? max : step < 0 ? min : (min + max) / 2;
    }
}

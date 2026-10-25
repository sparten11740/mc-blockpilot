package dev.blockpilot.game;

/**
 * Points inside a block in the block's own frame: the frame its model is drawn in facing north (x west to east, y up,
 * z north to south, each 0 to 1), turned about the block's vertical centre line in quarter turns clockwise seen from
 * above, as blockstate files turn models: north 0, east 1, south 2, west 3.
 */
public record BlockFrame(int quarterTurns) {
    public static final BlockFrame UNTURNED = new BlockFrame(0);

    /** A point inside a block, each coordinate 0 to 1. */
    public record Point(double x, double y, double z) {
    }

    /** The frame of a block whose horizontal facing has that name; any other name gives the unturned frame. */
    public static BlockFrame ofFacing(String facing) {
        return switch (facing) {
            case "east" -> new BlockFrame(1);
            case "south" -> new BlockFrame(2);
            case "west" -> new BlockFrame(3);
            default -> UNTURNED;
        };
    }

    /** Where a point given in this frame lies in the block, measured from its north-west bottom corner. */
    public Point toBlock(Point point) {
        double x = point.x();
        double z = point.z();
        for (int turn = 0; turn < Math.floorMod(quarterTurns, 4); turn++) {
            double turned = 1 - z;
            z = x;
            x = turned;
        }
        return new Point(x, point.y(), z);
    }

    /** The point in this frame of a point in the block, measured from its north-west bottom corner. */
    public Point fromBlock(Point point) {
        double x = point.x();
        double z = point.z();
        for (int turn = 0; turn < Math.floorMod(quarterTurns, 4); turn++) {
            double turned = z;
            z = 1 - x;
            x = turned;
        }
        return new Point(x, point.y(), z);
    }
}

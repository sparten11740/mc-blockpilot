package dev.blockpilot.api;

/**
 * A position token: {@code x,y,z} (absolute) or {@code ~dx,dy,dz} (relative to the player's block position). Printed
 * positions keep the form they were made with, so they can be pasted back into a command unchanged.
 */
public record Position(double x, double y, double z, boolean relative) {
    public static Position absolute(double x, double y, double z) {
        return new Position(x, y, z, false);
    }

    public static Position relative(double dx, double dy, double dz) {
        return new Position(dx, dy, dz, true);
    }

    /** The absolute position, given the player's block position for relative ones. */
    public Position resolve(int baseX, int baseY, int baseZ) {
        return relative ? absolute(baseX + x, baseY + y, baseZ + z) : this;
    }

    public int blockX() {
        return (int) Math.floor(x);
    }

    public int blockY() {
        return (int) Math.floor(y);
    }

    public int blockZ() {
        return (int) Math.floor(z);
    }

    @Override
    public String toString() {
        return (relative ? "~" : "") + Numbers.format(x) + "," + Numbers.format(y) + "," + Numbers.format(z);
    }

    /** Parses {@code x,y,z} or {@code ~dx,dy,dz}; returns null when the token is not a position. */
    public static Position parse(String token) {
        boolean relative = token.startsWith("~");
        String body = relative ? token.substring(1) : token;
        String[] parts = body.split(",", -1);
        if (parts.length != 3) {
            return null;
        }
        try {
            return new Position(Double.parseDouble(parts[0]), Double.parseDouble(parts[1]),
                Double.parseDouble(parts[2]), relative);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}

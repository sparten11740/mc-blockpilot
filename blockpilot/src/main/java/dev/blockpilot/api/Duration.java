package dev.blockpilot.api;

/** A duration token: {@code <n>t} in game ticks or {@code <n>s} in seconds (20 ticks each). */
public record Duration(int ticks) {
    public static final int TICKS_PER_SECOND = 20;

    public static Duration seconds(double seconds) {
        return new Duration((int) Math.round(seconds * TICKS_PER_SECOND));
    }

    public double seconds() {
        return ticks / (double) TICKS_PER_SECOND;
    }

    @Override
    public String toString() {
        return ticks + "t";
    }

    /** Parses {@code <n>t} or {@code <n>s}; returns null when the token is not a duration. */
    public static Duration parse(String token) {
        if (token.length() < 2) {
            return null;
        }
        char unit = token.charAt(token.length() - 1);
        String number = token.substring(0, token.length() - 1);
        try {
            return switch (unit) {
                case 't' -> new Duration(Integer.parseInt(number));
                case 's' -> seconds(Double.parseDouble(number));
                default -> null;
            };
        } catch (NumberFormatException e) {
            return null;
        }
    }
}

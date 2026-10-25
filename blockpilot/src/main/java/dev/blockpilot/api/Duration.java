package dev.blockpilot.api;

/** A duration token: {@code <n>t} in game ticks or {@code <n>s} in seconds (20 ticks each). */
public record Duration(int ticks) {
    public static final int TICKS_PER_SECOND = 20;

    public Duration {
        if (ticks < 0) {
            throw new IllegalArgumentException("a duration can't be negative: " + ticks);
        }
    }

    /** {@code seconds} rounded to ticks; it must be finite, not negative and at most 2^31 - 1 ticks. */
    public static Duration seconds(double seconds) {
        double ticks = seconds * TICKS_PER_SECOND;
        if (!(ticks >= 0 && Math.round(ticks) <= Integer.MAX_VALUE)) {
            throw new IllegalArgumentException("not a duration in seconds: " + seconds);
        }
        return new Duration((int) Math.round(ticks));
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
        if (!Lexicon.isDuration(token)) {
            return null;
        }
        String number = token.substring(0, token.length() - 1);
        return token.endsWith("t") ? new Duration(Integer.parseInt(number)) : seconds(Double.parseDouble(number));
    }
}

package dev.blockpilot.api;

/** A point on the open screen in GUI units, written {@code x,y}. */
public record Point(double x, double y) {
    @Override
    public String toString() {
        return Numbers.format(x) + "," + Numbers.format(y);
    }

    /** Parses {@code x,y}; returns null when the token is not a point. */
    public static Point parse(String token) {
        if (!Lexicon.isPoint(token)) {
            return null;
        }
        int comma = token.indexOf(',');
        return new Point(Double.parseDouble(token.substring(0, comma)), Double.parseDouble(token.substring(comma + 1)));
    }
}

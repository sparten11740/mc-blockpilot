package dev.blockpilot.game.observe;

import java.util.Arrays;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** {@code match=} patterns: the whole text fits, ignoring case, with {@code *} for any run of characters. */
public final class TextPattern {
    private TextPattern() {
    }

    public static Pattern compile(String pattern) {
        String regex = Arrays.stream(pattern.split("\\*", -1)).map(Pattern::quote).collect(Collectors.joining(".*"));
        return Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.DOTALL);
    }
}

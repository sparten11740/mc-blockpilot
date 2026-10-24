package dev.blockpilot.protocol;

import java.util.ArrayList;
import java.util.List;

/** One command of a script before its arguments are parsed: the name and its tokens, with their offsets. */
public final class RawCommand {
    /** A whitespace-separated token and where it starts in {@link #argumentText}. */
    public record Token(String text, int start) {
    }

    private final String name;
    private final String argumentText;
    private final List<Token> tokens;

    private RawCommand(String name, String argumentText, List<Token> tokens) {
        this.name = name;
        this.argumentText = argumentText;
        this.tokens = tokens;
    }

    /** Splits one command (already separated from the rest of the script) into its name and tokens. */
    public static RawCommand of(String command) {
        String trimmed = command.strip();
        int space = indexOfWhitespace(trimmed);
        String name = space < 0 ? trimmed : trimmed.substring(0, space);
        String argumentText = space < 0 ? "" : trimmed.substring(space).strip();
        List<Token> tokens = new ArrayList<>();
        int index = 0;
        while (index < argumentText.length()) {
            while (index < argumentText.length() && Character.isWhitespace(argumentText.charAt(index))) {
                index++;
            }
            int start = index;
            while (index < argumentText.length() && !Character.isWhitespace(argumentText.charAt(index))) {
                index++;
            }
            if (index > start) {
                tokens.add(new Token(argumentText.substring(start, index), start));
            }
        }
        return new RawCommand(name, argumentText, tokens);
    }

    private static int indexOfWhitespace(String text) {
        for (int index = 0; index < text.length(); index++) {
            if (Character.isWhitespace(text.charAt(index))) {
                return index;
            }
        }
        return -1;
    }

    public String name() {
        return name;
    }

    public String argumentText() {
        return argumentText;
    }

    public List<Token> tokens() {
        return tokens;
    }

    /** The argument text from the given token on, as free text. */
    public String restFrom(int tokenIndex) {
        return tokenIndex >= tokens.size() ? "" : argumentText.substring(tokens.get(tokenIndex).start());
    }

    /** A copy without the tokens equal to the given word (e.g. {@code full}). */
    public RawCommand without(String word) {
        StringBuilder rebuilt = new StringBuilder(name);
        for (Token token : tokens) {
            if (!token.text().equals(word)) {
                rebuilt.append(' ').append(token.text());
            }
        }
        return of(rebuilt.toString());
    }

    public boolean has(String word) {
        return tokens.stream().anyMatch(token -> token.text().equals(word));
    }

    @Override
    public String toString() {
        return argumentText.isEmpty() ? name : name + " " + argumentText;
    }
}

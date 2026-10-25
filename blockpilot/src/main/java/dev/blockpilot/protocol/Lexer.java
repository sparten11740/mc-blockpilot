package dev.blockpilot.protocol;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits a script into steps of tokens: the grammar's {@code script}, {@code separator}, {@code step} and
 * {@code token} productions. {@code \;} is resolved to a semicolon that doesn't end the step.
 */
final class Lexer {
    /**
     * A token as written, with {@code \;} resolved; {@code column} counts from the step's first non-blank character
     * (1-based, escapes as written), {@code offset} indexes the step's source.
     */
    record Token(String text, int column, int offset) {
    }

    /** A step's text from its first non-blank character, escapes resolved and trailing blanks dropped. */
    record Step(String source, List<Token> tokens) {
    }

    private Lexer() {
    }

    static List<Step> steps(String script) {
        List<Step> steps = new ArrayList<>();
        StepBuilder current = new StepBuilder();
        for (int index = 0; index < script.length(); index++) {
            char character = script.charAt(index);
            if (character == '\\' && index + 1 < script.length() && script.charAt(index + 1) == ';') {
                current.add(';', 2);
                index++;
            } else if (character == ';' || character == '\n' || character == '\r') {
                current.finish(steps);
                current = new StepBuilder();
            } else {
                current.add(character, 1);
            }
        }
        current.finish(steps);
        return steps;
    }

    private static boolean isBlank(char character) {
        return character == ' ' || character == '\t';
    }

    private static final class StepBuilder {
        private final StringBuilder source = new StringBuilder();
        private final List<Token> tokens = new ArrayList<>();
        private final StringBuilder token = new StringBuilder();
        private int column;
        private int tokenColumn;
        private int tokenOffset;

        /** A character as written ({@code width} 2 for an escaped semicolon). */
        void add(char character, int width) {
            if (isBlank(character)) {
                if (source.isEmpty()) {
                    return;
                }
                endToken();
            } else {
                if (token.isEmpty()) {
                    tokenColumn = column + 1;
                    tokenOffset = source.length();
                }
                token.append(character);
            }
            source.append(character);
            column += width;
        }

        private void endToken() {
            if (!token.isEmpty()) {
                tokens.add(new Token(token.toString(), tokenColumn, tokenOffset));
                token.setLength(0);
            }
        }

        void finish(List<Step> steps) {
            endToken();
            if (!tokens.isEmpty()) {
                steps.add(new Step(source.toString().stripTrailing(), List.copyOf(tokens)));
            }
        }
    }
}

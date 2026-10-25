package dev.blockpilot.api;

/**
 * The lexical productions of the command language's grammar (see the {@code command-language} spec), one recognizer
 * each, written as a scanner that follows its production. Whole numbers must also fit a 32-bit int.
 */
public final class Lexicon {
    /** A test token split into its parts: {@code field-path comparison operand}. */
    public record Test(String field, String comparison, String operand) {
    }

    /** Two-character comparisons first, so the longest one is found first. */
    private static final String[] COMPARISONS = {"==", "!=", "<=", ">=", "<", ">"};
    private static final String NAMESPACE_CHARACTERS = "abcdefghijklmnopqrstuvwxyz0123456789_.-";
    private static final String PATH_CHARACTERS = NAMESPACE_CHARACTERS + "/";
    private static final String PATTERN_CHARACTERS = PATH_CHARACTERS + ":*";
    private static final String HEX_DIGITS = "0123456789abcdefABCDEF";

    private Lexicon() {
    }

    /** {@code position ::= "~"? decimal "," decimal "," decimal} */
    public static boolean isPosition(String token) {
        Scanner scanner = new Scanner(token);
        scanner.accept('~');
        return scanner.decimals(3) && scanner.atEnd();
    }

    /** {@code point ::= decimal "," decimal} */
    public static boolean isPoint(String token) {
        Scanner scanner = new Scanner(token);
        return scanner.decimals(2) && scanner.atEnd();
    }

    /** {@code entity ::= "e" digits} */
    public static boolean isEntity(String token) {
        return isNumbered(token, 'e');
    }

    /** {@code widget ::= "w" digits} */
    public static boolean isWidget(String token) {
        return isNumbered(token, 'w');
    }

    /** {@code slot ::= "s" digits} */
    public static boolean isSlot(String token) {
        return isNumbered(token, 's');
    }

    /** {@code count ::= "x" digits} */
    public static boolean isCount(String token) {
        return isNumbered(token, 'x');
    }

    private static boolean isNumbered(String token, char prefix) {
        Scanner scanner = new Scanner(token);
        return scanner.accept(prefix) && scanner.wholeNumber(Integer.MAX_VALUE) && scanner.atEnd();
    }

    /** {@code uuid ::= hex8 "-" hex4 "-" hex4 "-" hex4 "-" hex8 hex4} */
    public static boolean isUuid(String token) {
        Scanner scanner = new Scanner(token);
        int[] groups = {8, 4, 4, 4, 12};
        for (int group = 0; group < groups.length; group++) {
            if (group > 0 && !scanner.accept('-') || !scanner.hex(groups[group])) {
                return false;
            }
        }
        return scanner.atEnd();
    }

    /** {@code integer ::= "-"? digits} */
    public static boolean isInteger(String token) {
        Scanner scanner = new Scanner(token);
        long limit = scanner.accept('-') ? -(long) Integer.MIN_VALUE : Integer.MAX_VALUE;
        return scanner.wholeNumber(limit) && scanner.atEnd();
    }

    /** {@code decimal ::= "-"? digits ( "." digits )?}, also the {@code number} slot type. */
    public static boolean isDecimal(String token) {
        Scanner scanner = new Scanner(token);
        return scanner.decimal() && scanner.atEnd();
    }

    /** {@code duration ::= digits "t" | digits ( "." digits )? "s"}, at most a 32-bit int of ticks. */
    public static boolean isDuration(String token) {
        Scanner scanner = new Scanner(token);
        if (scanner.wholeNumber(Integer.MAX_VALUE) && scanner.accept('t') && scanner.atEnd()) {
            return true;
        }
        scanner = new Scanner(token);
        if (!(scanner.unsignedDecimal() && scanner.accept('s') && scanner.atEnd())) {
            return false;
        }
        double ticks = Double.parseDouble(token.substring(0, token.length() - 1)) * Duration.TICKS_PER_SECOND;
        return Math.abs(Math.round(ticks)) <= Integer.MAX_VALUE;
    }

    /** {@code id ::= ( namespace ":" )? path}, {@code namespace ::= [a-z0-9_.-]+}, {@code path ::= [a-z0-9_./-]+} */
    public static boolean isId(String token) {
        Scanner scanner = new Scanner(token);
        int namespace = scanner.run(NAMESPACE_CHARACTERS);
        if (scanner.accept(':')) {
            return namespace > 0 && scanner.run(PATH_CHARACTERS) > 0 && scanner.atEnd();
        }
        return namespace + scanner.run(PATH_CHARACTERS) > 0 && scanner.atEnd();
    }

    /** {@code tag ::= "#" id} */
    public static boolean isTag(String token) {
        return token.startsWith("#") && isId(token.substring(1));
    }

    /** {@code pattern ::= [a-z0-9_.:/*-]* "*" [a-z0-9_.:/*-]*} */
    public static boolean isPattern(String token) {
        Scanner scanner = new Scanner(token);
        return scanner.run(PATTERN_CHARACTERS) > 0 && scanner.atEnd() && token.indexOf('*') >= 0;
    }

    /** {@code identifier ::= [A-Za-z_] [A-Za-z0-9_]*} */
    public static boolean isIdentifier(String token) {
        Scanner scanner = new Scanner(token);
        return scanner.identifier() && scanner.atEnd();
    }

    /** {@code field-path ::= identifier ( "." ( identifier | digits ) )*} */
    public static boolean isFieldPath(String token) {
        Scanner scanner = new Scanner(token);
        return scanner.fieldPath() && scanner.atEnd();
    }

    /**
     * {@code option ::= option-name "=" ( ( token-char - "=" ) token-char* )?}, with
     * {@code option-name ::= identifier ( ":" identifier )?}: the index of the {@code =}, or -1 for other tokens.
     */
    public static int optionEquals(String token) {
        Scanner scanner = new Scanner(token);
        if (!scanner.identifier()) {
            return -1;
        }
        int beforeColon = scanner.position;
        if (scanner.accept(':') && !scanner.identifier()) {
            scanner.position = beforeColon;
        }
        int equals = scanner.position;
        if (!scanner.accept('=') || scanner.accept('=')) {
            return -1;
        }
        return equals;
    }

    /** {@code test ::= field-path comparison operand}, split into its parts, or null for other tokens. */
    public static Test test(String token) {
        Scanner scanner = new Scanner(token);
        if (!scanner.fieldPath()) {
            return null;
        }
        int fieldEnd = scanner.position;
        String comparison = scanner.first(COMPARISONS);
        if (comparison == null || scanner.atEnd()) {
            return null;
        }
        return new Test(token.substring(0, fieldEnd), comparison, token.substring(scanner.position));
    }

    /** {@code word ::= token - ( option | test )} */
    public static boolean isWord(String token) {
        return !token.isEmpty() && optionEquals(token) < 0 && test(token) == null;
    }

    /** Reads a token from left to right; a method that doesn't match leaves the position where it was. */
    private static final class Scanner {
        private final String text;
        private int position;

        Scanner(String text) {
            this.text = text;
        }

        boolean atEnd() {
            return position == text.length();
        }

        boolean accept(char expected) {
            if (position < text.length() && text.charAt(position) == expected) {
                position++;
                return true;
            }
            return false;
        }

        /** The first of the words that starts here, consumed, or null. */
        String first(String[] words) {
            for (String word : words) {
                if (text.startsWith(word, position)) {
                    position += word.length();
                    return word;
                }
            }
            return null;
        }

        /** Consumes characters from the set; returns how many. */
        int run(String characters) {
            int start = position;
            while (position < text.length() && characters.indexOf(text.charAt(position)) >= 0) {
                position++;
            }
            return position - start;
        }

        boolean digits() {
            return run("0123456789") > 0;
        }

        /** {@code digits} whose value is at most the limit. */
        boolean wholeNumber(long limit) {
            int start = position;
            long value = 0;
            while (position < text.length() && text.charAt(position) >= '0' && text.charAt(position) <= '9') {
                value = value * 10 + text.charAt(position) - '0';
                position++;
                if (value > limit) {
                    position = start;
                    return false;
                }
            }
            return position > start;
        }

        /** {@code decimal ( "," decimal )*}, that many. */
        boolean decimals(int count) {
            for (int index = 0; index < count; index++) {
                if (index > 0 && !accept(',') || !decimal()) {
                    return false;
                }
            }
            return true;
        }

        boolean decimal() {
            int start = position;
            accept('-');
            if (!unsignedDecimal()) {
                position = start;
                return false;
            }
            return true;
        }

        boolean unsignedDecimal() {
            if (!digits()) {
                return false;
            }
            int beforePoint = position;
            if (accept('.') && !digits()) {
                position = beforePoint;
            }
            return true;
        }

        boolean hex(int count) {
            int start = position;
            for (int index = 0; index < count; index++) {
                if (position >= text.length() || HEX_DIGITS.indexOf(text.charAt(position)) < 0) {
                    position = start;
                    return false;
                }
                position++;
            }
            return true;
        }

        boolean identifier() {
            if (position >= text.length() || !isIdentifierStart(text.charAt(position))) {
                return false;
            }
            position++;
            while (position < text.length() && isIdentifierPart(text.charAt(position))) {
                position++;
            }
            return true;
        }

        boolean fieldPath() {
            if (!identifier()) {
                return false;
            }
            while (true) {
                int beforeDot = position;
                if (!accept('.') || !(identifier() || digits())) {
                    position = beforeDot;
                    return true;
                }
            }
        }

        private static boolean isIdentifierStart(char character) {
            return character >= 'a' && character <= 'z' || character >= 'A' && character <= 'Z' || character == '_';
        }

        private static boolean isIdentifierPart(char character) {
            return isIdentifierStart(character) || character >= '0' && character <= '9';
        }
    }
}

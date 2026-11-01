package dev.blockpilot.api;

/**
 * The lexical productions of the command language's grammar (see the {@code command-language} spec), one recognizer
 * each, written as a scanner that follows its production, with the grammar's value limits: whole numbers below 2^31,
 * at most 9 digits before a decimal point, durations of at most 2^31 - 1 ticks.
 */
public final class Lexicon {
    /** A token of the {@code test-shape} production split into its parts: {@code field-path comparison operand}. */
    public record Test(String field, String comparison, String operand) {
        /** The field a {@code $<field>} operand names, or null when the operand is a value. */
        public String reference() {
            return operand.startsWith("$") && isFieldPath(operand.substring(1)) ? operand.substring(1) : null;
        }

        public boolean isOrdering() {
            return !comparison.equals("==") && !comparison.equals("!=");
        }
    }

    /** Two-character comparisons first, so the longest one is found first. */
    private static final String[] COMPARISONS = {"==", "!=", "<=", ">=", "<", ">"};
    private static final String NAMESPACE_CHARACTERS = "abcdefghijklmnopqrstuvwxyz0123456789_.-";
    private static final String PATH_CHARACTERS = NAMESPACE_CHARACTERS + "/";
    private static final String PATTERN_CHARACTERS = PATH_CHARACTERS + ":*";
    private static final String HEX_DIGITS = "0123456789abcdefABCDEF";
    private static final String NAME_CHARACTERS = "abcdefghijklmnopqrstuvwxyz0123456789_";
    private static final int DECIMAL_DIGITS = 9;

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

    /** {@code menu-slot ::= "s" digits} */
    public static boolean isMenuSlot(String token) {
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

    /** Digits after the first character, whatever the number, so a too-large reference isn't taken for an id. */
    private static boolean hasNumberedShape(String token) {
        Scanner scanner = new Scanner(token.substring(1));
        return scanner.digits() && scanner.atEnd();
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

    /** {@code duration ::= digits "t" | seconds "s"} */
    public static boolean isDuration(String token) {
        Scanner scanner = new Scanner(token);
        if (scanner.wholeNumber(Integer.MAX_VALUE) && scanner.accept('t') && scanner.atEnd()) {
            return true;
        }
        return token.endsWith("s") && isSeconds(token.substring(0, token.length() - 1));
    }

    /** {@code seconds ::= digits ( "." digits )?}, at most 2^31 - 1 ticks. */
    public static boolean isSeconds(String token) {
        Scanner scanner = new Scanner(token);
        return scanner.unsignedDecimal() && scanner.atEnd()
            && Math.round(Double.parseDouble(token) * Duration.TICKS_PER_SECOND) <= Integer.MAX_VALUE;
    }

    /** {@code id ::= namespace ":" path | bare-id}, where a bare id never has another slot type's shape. */
    public static boolean isId(String token) {
        int colon = token.indexOf(':');
        if (colon >= 0) {
            return isNamespace(token.substring(0, colon)) && isPath(token.substring(colon + 1));
        }
        return isPath(token) && !hasNumberShape(token) && !isUuid(token);
    }

    /**
     * Whether the token has the shape {@code bare-id} excludes, whatever its numbers: a decimal, a count or reference
     * ({@code x}, {@code e}, {@code w}, {@code s} and digits) or a duration.
     */
    private static boolean hasNumberShape(String token) {
        char first = token.charAt(0);
        if ("xews".indexOf(first) >= 0) {
            return hasNumberedShape(token);
        }
        Scanner scanner = new Scanner(token);
        boolean signed = scanner.accept('-');
        if (!scanner.digits()) {
            return false;
        }
        int beforePoint = scanner.position;
        boolean point = scanner.accept('.') && scanner.digits();
        if (!point) {
            scanner.position = beforePoint;
        }
        if (scanner.atEnd()) {
            return true;
        }
        char unit = token.charAt(scanner.position);
        return !signed && (unit == 's' || unit == 't' && !point) && scanner.position == token.length() - 1;
    }

    /** {@code namespace ::= [a-z0-9_.-]+} */
    public static boolean isNamespace(String token) {
        Scanner scanner = new Scanner(token);
        return scanner.run(NAMESPACE_CHARACTERS) > 0 && scanner.atEnd();
    }

    private static boolean isPath(String token) {
        Scanner scanner = new Scanner(token);
        return scanner.run(PATH_CHARACTERS) > 0 && scanner.atEnd();
    }

    /** {@code name ::= [a-z] [a-z0-9_]*}, a command's name without its namespace. */
    public static boolean isName(String token) {
        return !token.isEmpty() && token.charAt(0) >= 'a' && token.charAt(0) <= 'z'
            && new Scanner(token).run(NAME_CHARACTERS) == token.length();
    }

    /** {@code tag ::= "#" id} */
    public static boolean isTag(String token) {
        return token.startsWith("#") && isId(token.substring(1));
    }

    /** {@code pattern ::= ( [a-z0-9_.:/-]* "*" )+ [a-z0-9_.:/-]*} */
    public static boolean isPattern(String token) {
        Scanner scanner = new Scanner(token);
        return scanner.run(PATTERN_CHARACTERS) > 0 && scanner.atEnd() && token.indexOf('*') >= 0;
    }

    /**
     * {@code block-state ::= ( pattern | tag | id ) "[" state ( "," state )* "]"}, with
     * {@code state ::= state-name "=" state-value} and both names {@code [a-z0-9_]+}.
     */
    public static boolean isBlockState(String token) {
        int open = token.indexOf('[');
        if (open < 0 || !token.endsWith("]")) {
            return false;
        }
        String block = token.substring(0, open);
        if (!isPattern(block) && !isTag(block) && !isId(block)) {
            return false;
        }
        for (String state : token.substring(open + 1, token.length() - 1).split(",", -1)) {
            int equals = state.indexOf('=');
            if (equals < 0 || !isStateWord(state.substring(0, equals)) || !isStateWord(state.substring(equals + 1))) {
                return false;
            }
        }
        return true;
    }

    /** {@code [a-z0-9_]+}, a state's name or value. */
    private static boolean isStateWord(String token) {
        Scanner scanner = new Scanner(token);
        return scanner.run(NAME_CHARACTERS) > 0 && scanner.atEnd();
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
     * {@code option ::= option-name "=" ( token-char - "=" ) token-char*}, with
     * {@code option-name ::= identifier ( ":" identifier )?}: the index of the {@code =}, or -1 for other tokens.
     */
    public static int optionEquals(String token) {
        Scanner scanner = new Scanner(token);
        if (!scanner.optionName()) {
            return -1;
        }
        int equals = scanner.position;
        if (!scanner.accept('=') || scanner.atEnd() || scanner.accept('=')) {
            return -1;
        }
        return equals;
    }

    /** {@code option-name ::= identifier ( ":" identifier )?} */
    public static boolean isOptionName(String token) {
        Scanner scanner = new Scanner(token);
        return scanner.optionName() && scanner.atEnd();
    }

    /** {@code test-shape ::= field-path comparison token-char+}, split into its parts, or null for other tokens. */
    public static Test testShape(String token) {
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

    /**
     * {@code test ::= field-path ( ( "==" | "!=" ) operand | ( "<=" | ">=" | "<" | ">" ) ( decimal | reference ) )},
     * with {@code operand ::= reference | value} and
     * {@code value ::= ( token-char - [$<>=!&|] ) ( token-char - [<>=!&|] )*}.
     */
    public static boolean isTest(String token) {
        Test test = testShape(token);
        if (test == null) {
            return false;
        }
        if (test.reference() != null) {
            return true;
        }
        return test.isOrdering() ? isDecimal(test.operand()) : isValue(test.operand());
    }

    private static boolean isValue(String operand) {
        for (int index = 0; index < operand.length(); index++) {
            if ((index == 0 ? "$<>=!&|" : "<>=!&|").indexOf(operand.charAt(index)) >= 0) {
                return false;
            }
        }
        return true;
    }

    /** {@code word ::= token - ( option | test-shape )} */
    public static boolean isWord(String token) {
        return !token.isEmpty() && optionEquals(token) < 0 && testShape(token) == null;
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
            int start = position;
            if (!digits()) {
                return false;
            }
            if (position - start > DECIMAL_DIGITS) {
                position = start;
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
                if (!accept('.') || !(identifier() || wholeNumber(Integer.MAX_VALUE))) {
                    position = beforeDot;
                    return true;
                }
            }
        }

        boolean optionName() {
            if (!identifier()) {
                return false;
            }
            int beforeColon = position;
            if (accept(':') && !identifier()) {
                position = beforeColon;
            }
            return true;
        }

        private static boolean isIdentifierStart(char character) {
            return character >= 'a' && character <= 'z' || character >= 'A' && character <= 'Z' || character == '_';
        }

        private static boolean isIdentifierPart(char character) {
            return isIdentifierStart(character) || character >= '0' && character <= '9';
        }
    }
}

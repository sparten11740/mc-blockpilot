package dev.blockpilot.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The token productions of the spec's EBNF, translated to regular expressions with the spec's reading (ordered
 * alternatives as atomic groups, possessive repetition), accept exactly what the lexicon's recognizers accept.
 * Generated input stays within 8 characters, so the value limits (tested in LexiconTest) never apply.
 */
class SpecGrammarTest {
    private static final Map<String, Predicate<String>> RECOGNIZERS = new LinkedHashMap<>();

    static {
        RECOGNIZERS.put("position", Lexicon::isPosition);
        RECOGNIZERS.put("point", Lexicon::isPoint);
        RECOGNIZERS.put("entity", Lexicon::isEntity);
        RECOGNIZERS.put("widget", Lexicon::isWidget);
        RECOGNIZERS.put("menu-slot", Lexicon::isMenuSlot);
        RECOGNIZERS.put("uuid", Lexicon::isUuid);
        RECOGNIZERS.put("integer", Lexicon::isInteger);
        RECOGNIZERS.put("decimal", Lexicon::isDecimal);
        RECOGNIZERS.put("count", Lexicon::isCount);
        RECOGNIZERS.put("duration", Lexicon::isDuration);
        RECOGNIZERS.put("seconds", Lexicon::isSeconds);
        RECOGNIZERS.put("id", Lexicon::isId);
        RECOGNIZERS.put("tag", Lexicon::isTag);
        RECOGNIZERS.put("pattern", Lexicon::isPattern);
        RECOGNIZERS.put("identifier", Lexicon::isIdentifier);
        RECOGNIZERS.put("field-path", Lexicon::isFieldPath);
        RECOGNIZERS.put("option", token -> Lexicon.optionEquals(token) >= 0);
        RECOGNIZERS.put("option-name", Lexicon::isOptionName);
        RECOGNIZERS.put("test", Lexicon::isTest);
        RECOGNIZERS.put("test-shape", token -> Lexicon.testShape(token) != null);
        RECOGNIZERS.put("word", Lexicon::isWord);
        RECOGNIZERS.put("name", Lexicon::isName);
        RECOGNIZERS.put("namespace", Lexicon::isNamespace);
    }

    private static final String ALPHABET = "0123456789aestwxzAZ~,.-:#*/_=<>!$&|";
    private static final List<String> EXAMPLES = List.of("1,2,3", "~0,-1,0", "-1.5,64", "e812", "w3", "s5", "x6",
        "0f3a9c2e-1b2c-4d5e-8f90-a1b2c3d4e5f6", "0F3A9C2E-1B2C-4D5E-8F90-A1B2C3D4E5F6", "-12", "1.5", "20t", "0.5s",
        "stone", "minecraft:stone", "c:ores/iron", "#logs", "*ore*", "create:*", "max_hp", "rows.0.id", "near=2",
        "rule:keepInventory=true", "id==air", "hp<$max_hp", "hp<max_hp", "a>==b", "pos==1,2,3", "full", "5.5t",
        "-5t", "5.", "since=", "mine", "my.mod-id_2", "stone[facing=north]");

    private static Map<String, String> productions;
    private static final Map<String, String> REGEXES = new HashMap<>();

    @BeforeAll
    static void readTheSpec() throws IOException {
        Path root = Paths.get("").toAbsolutePath();
        while (!Files.exists(root.resolve("settings.gradle.kts"))) {
            root = root.getParent();
        }
        Path archived = root.resolve("openspec/specs/command-language/spec.md");
        Path change = root.resolve("openspec/changes/add-agent-control-mod/specs/command-language/spec.md");
        String spec = Files.readString(Files.exists(archived) ? archived : change);
        int start = spec.indexOf("```ebnf");
        String block = spec.substring(start + "```ebnf".length(), spec.indexOf("```", start + 3));
        productions = Ebnf.productions(block.replaceAll("(?s)/\\*.*?\\*/", ""));
    }

    @Test
    void everyRecognizedProductionIsInTheSpec() {
        for (String name : RECOGNIZERS.keySet()) {
            assertTrue(productions.containsKey(name), name + " is no longer a production of the spec");
        }
    }

    @Test
    void theRecognizersAcceptWhatTheProductionsDescribe() {
        Random random = new Random(1703);
        List<String> inputs = new ArrayList<>(EXAMPLES);
        for (int index = 0; index < 30_000; index++) {
            StringBuilder token = new StringBuilder();
            for (int length = 1 + random.nextInt(8); length > 0; length--) {
                token.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
            }
            inputs.add(token.toString());
        }
        RECOGNIZERS.forEach((name, recognizer) -> {
            Pattern pattern = Pattern.compile(regex(name));
            for (String input : inputs) {
                assertEquals(pattern.matcher(input).matches(), recognizer.test(input), name + " on " + input);
            }
        });
    }

    private static String regex(String name) {
        String cached = REGEXES.get(name);
        if (cached == null) {
            cached = new Ebnf(productions.get(name), SpecGrammarTest::regex).translate();
            REGEXES.put(name, cached);
        }
        return cached;
    }

    /** Just enough W3C EBNF to translate the token productions: references, literals, classes and operators. */
    private static final class Ebnf {
        private final String text;
        private final Function<String, String> references;
        private int position;
        /** Set while reading the right side of {@code A - B}, for the choice that opens it. */
        private boolean excluding;

        Ebnf(String text, Function<String, String> references) {
            this.text = text;
            this.references = references;
        }

        static Map<String, String> productions(String block) {
            Map<String, String> productions = new LinkedHashMap<>();
            String[] parts = block.split("(?m)^(?=[a-z][a-z0-9-]*\\s+::=)");
            for (String part : parts) {
                int separator = part.indexOf("::=");
                if (separator > 0) {
                    productions.put(part.substring(0, separator).strip(), part.substring(separator + 3).strip());
                }
            }
            return productions;
        }

        /** The whole right-hand side; an exception at the top applies to the whole token. */
        String translate() {
            String result = choice(true);
            if (position < text.length()) {
                throw new IllegalStateException("unread EBNF: " + text.substring(position));
            }
            return result;
        }

        private String choice(boolean whole) {
            boolean atomic = !excluding;
            excluding = false;
            List<String> alternatives = new ArrayList<>();
            alternatives.add(sequence(whole));
            while (peek() == '|') {
                position++;
                alternatives.add(sequence(whole));
            }
            // An atomic group: the first alternative that matches is taken and never revisited, as the spec reads.
            // What a whole token is checked against (A - B) is any alternative of B matching the whole token.
            String group = atomic ? "(?>" : "(?:";
            return alternatives.size() == 1 ? alternatives.get(0) : group + String.join("|", alternatives) + ")";
        }

        private String sequence(boolean whole) {
            StringBuilder out = new StringBuilder();
            int items = 0;
            while (position < text.length() && peek() != '|' && peek() != ')') {
                String item = postfixed();
                if (peek() == '-') {
                    position++;
                    excluding = true;
                    String excluded = postfixed();
                    excluding = false;
                    // A whole-token exception looks at the end of the input, a character's at one character.
                    boolean wholeToken = whole && items == 0 && (position >= text.length() || peek() == '|');
                    item = "(?!(?:" + excluded + ")" + (wholeToken ? "$" : "") + ")(?:" + item + ")";
                }
                out.append(item);
                items++;
            }
            return out.toString();
        }

        private String postfixed() {
            String primary = primary();
            char next = position < text.length() ? text.charAt(position) : ' ';
            if (next == '?' || next == '*' || next == '+') {
                position++;
                // Possessive: as much as it can, never given back.
                return "(?:" + primary + ")" + next + "+";
            }
            return primary;
        }

        private String primary() {
            char next = peek();
            if (next == '(') {
                position++;
                String inner = choice(false);
                expect(')');
                return "(?:" + inner + ")";
            }
            if (next == '"' || next == '\'') {
                int end = text.indexOf(next, position + 1);
                String literal = text.substring(position + 1, end);
                position = end + 1;
                return Pattern.quote(literal);
            }
            if (next == '[') {
                int end = text.indexOf(']', position);
                String body = text.substring(position + 1, end);
                position = end + 1;
                return "[" + body.replaceAll("#x([0-9A-Fa-f]+)", "\\\\x{$1}").replace("[", "\\[") + "]";
            }
            if (next == '#') {
                int start = position + 2;
                position = start;
                while (position < text.length() && Character.isLetterOrDigit(text.charAt(position))) {
                    position++;
                }
                return "\\x{" + text.substring(start, position) + "}";
            }
            int start = position;
            while (position < text.length() && (Character.isLetterOrDigit(text.charAt(position))
                || text.charAt(position) == '-' && position + 1 < text.length()
                && Character.isLetter(text.charAt(position + 1)))) {
                position++;
            }
            return "(?:" + references.apply(text.substring(start, position)) + ")";
        }

        private char peek() {
            while (position < text.length() && Character.isWhitespace(text.charAt(position))) {
                position++;
            }
            return position < text.length() ? text.charAt(position) : '\0';
        }

        private void expect(char character) {
            if (peek() != character) {
                throw new IllegalStateException("expected " + character + " at " + text.substring(position));
            }
            position++;
        }
    }
}

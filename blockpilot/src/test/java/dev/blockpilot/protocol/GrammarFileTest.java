package dev.blockpilot.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bottlecaps.railroad.core.Parser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** docs/grammar.ebnf, the entire grammar as help grammar prints it, and its diagrams in docs/grammar.xhtml. */
class GrammarFileTest {
    private static final Pattern PRODUCTION = Pattern.compile("^([A-Za-z_][A-Za-z0-9._-]*) ::= (.*)$");
    private static final Pattern NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9._-]*");
    /** What a right-hand side holds besides names: comments, quoted literals, character classes and #x escapes. */
    private static final Pattern NOT_NAMES = Pattern.compile("/\\*.*?\\*/|\"[^\"]*\"|'[^']*'|\\[[^\\]]*\\]|#x[0-9A-Fa-f]+");
    private static final Pattern DIAGRAM = Pattern.compile("<a name=\"([^\"]+)\"");
    private static final String GENERATOR_ANCHOR = "Railroad-Diagram-Generator";

    private static Path root() {
        Path root = Paths.get("").toAbsolutePath();
        while (!Files.exists(root.resolve("settings.gradle.kts"))) {
            root = root.getParent();
        }
        return root;
    }

    static String grammar() throws IOException {
        return Files.readString(root().resolve("docs/grammar.ebnf"));
    }

    /** The syntax error rr-lib's W3C EBNF parser reports, or null. */
    static String syntaxError(String grammar) {
        Parser parser = new Parser(grammar, new Parser.EventHandler() {
            @Override
            public void reset(CharSequence input) {
            }

            @Override
            public void startNonterminal(String name, int begin) {
            }

            @Override
            public void endNonterminal(String name, int end) {
            }

            @Override
            public void terminal(String name, int begin, int end) {
            }

            @Override
            public void whitespace(int begin, int end) {
            }
        });
        try {
            parser.parse_Grammar();
            return null;
        } catch (Parser.ParseException e) {
            return parser.getErrorMessage(e);
        }
    }

    /** Each production's name with its right-hand sides, in order; a name defined twice has two. */
    static Map<String, List<String>> productions(String grammar) {
        Map<String, List<String>> productions = new LinkedHashMap<>();
        for (String line : grammar.lines().toList()) {
            Matcher matcher = PRODUCTION.matcher(line);
            if (matcher.matches()) {
                productions.computeIfAbsent(matcher.group(1), name -> new ArrayList<>()).add(matcher.group(2));
            }
        }
        return productions;
    }

    static Set<String> references(String rightHandSide) {
        Set<String> names = new LinkedHashSet<>();
        Matcher matcher = NAME.matcher(NOT_NAMES.matcher(rightHandSide).replaceAll(" "));
        while (matcher.find()) {
            names.add(matcher.group());
        }
        return names;
    }

    /** Names defined more than once, referred to but not defined, and defined but unreachable. */
    static List<String> problems(String grammar) {
        Map<String, List<String>> productions = productions(grammar);
        List<String> problems = new ArrayList<>();
        productions.forEach((name, bodies) -> {
            if (bodies.size() > 1) {
                problems.add("defined " + bodies.size() + " times: " + name);
            }
            bodies.forEach(body -> references(body).stream().filter(reference -> !productions.containsKey(reference))
                .forEach(reference -> problems.add("undefined: " + reference + " in " + name)));
        });
        Set<String> reached = new LinkedHashSet<>();
        Deque<String> pending = new ArrayDeque<>();
        productions.keySet().stream()
            .filter(name -> name.equals("connection") || name.equals("script") || name.endsWith("-command")
                || name.endsWith("-option"))
            .forEach(pending::add);
        while (!pending.isEmpty()) {
            String name = pending.pop();
            if (reached.add(name)) {
                productions.getOrDefault(name, List.of()).forEach(body -> pending.addAll(references(body)));
            }
        }
        productions.keySet().stream().filter(name -> !reached.contains(name))
            .forEach(name -> problems.add("unreachable: " + name));
        return problems;
    }

    @Test
    void theFileIsW3cEbnf() throws IOException {
        assertEquals(null, syntaxError(grammar()));
    }

    @Test
    void everyNameIsDefinedOnceAndReached() throws IOException {
        assertEquals(List.of(), problems(grammar()));
    }

    @Test
    void theCoreIsTheSpecsAndTheEngineCommandsAreTheGeneratedOnes() throws IOException {
        List<String> lines = grammar().lines().toList();
        // The file's header comment, then the core, then a blank line before the commands' section.
        List<String> core = lines.subList(1, lines.indexOf(""));
        assertEquals(Grammar.coreLines(), core);
        assertTrue(lines.contains(Grammar.COMMANDS_COMMENT));
        CommandRegistry registry = new CommandRegistry();
        new Engine(registry, new FakeGame());
        registry.all().forEach(entry -> Grammar.command(entry)
            .forEach(production -> assertTrue(lines.contains(production), "missing: " + production)));
    }

    @Test
    void theDiagramsShowExactlyTheProductions() throws IOException {
        Set<String> diagrams = new LinkedHashSet<>();
        Matcher matcher = DIAGRAM.matcher(Files.readString(root().resolve("docs/grammar.xhtml")));
        while (matcher.find()) {
            diagrams.add(matcher.group(1));
        }
        diagrams.remove(GENERATOR_ANCHOR);
        assertEquals(productions(grammar()).keySet(), diagrams, "./gradlew grammarDiagrams redraws them");
    }

    @Test
    void theChecksCatchBrokenGrammars() {
        String valid = "script ::= step ( \";\" step )*\nstep ::= \"go\" | name\nname ::= [a-z]+\n";
        assertEquals(null, syntaxError(valid));
        assertEquals(List.of(), problems(valid));
        assertTrue(syntaxError(valid + "broken ::= ( \"a\" | \"b\"\n") != null);
        assertEquals(List.of("defined 2 times: name"), problems(valid + "name ::= [0-9]+\n"));
        assertEquals(List.of("undefined: missing in step"),
            problems(valid.replace("\"go\" | name", "\"go\" | missing | name")));
        assertEquals(List.of("unreachable: spare"), problems(valid + "spare ::= \"x\"\n"));
        assertEquals(Set.of("sable.find-command", "word"), references("\"a\" sable.find-command /* b c */ [d] word"));
    }
}

package dev.blockpilot.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.blockpilot.api.Action;
import dev.blockpilot.api.ArgType;
import dev.blockpilot.api.BlockpilotApi;
import dev.blockpilot.api.CommandSpec;
import dev.blockpilot.api.Form;
import dev.blockpilot.api.Result;
import dev.blockpilot.protocol.GrammarReading.CharClass;
import dev.blockpilot.protocol.GrammarReading.Choice;
import dev.blockpilot.protocol.GrammarReading.Except;
import dev.blockpilot.protocol.GrammarReading.Literal;
import dev.blockpilot.protocol.GrammarReading.Node;
import dev.blockpilot.protocol.GrammarReading.Reference;
import dev.blockpilot.protocol.GrammarReading.Repeat;
import dev.blockpilot.protocol.GrammarReading.Sequence;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * The parser against docs/grammar.ebnf: scripts derived from the file's productions, valid and slightly broken, are
 * accepted or refused alike by the parser and by {@link GrammarReading}. The game's commands can't be registered
 * without the game, so the parser gets them rebuilt from the file; the smoke test holds the real ones to the file.
 */
class GrammarConformanceTest {
    private static final CommandSpec.QueryHandler QUERY = (context, args) -> Result.ok();
    private static final CommandSpec.ActionHandler ACTION = (context, args) -> (Action) () -> Result.ok();
    /**
     * What the parser also refuses by rules the productions don't state, and never the reverse: what may run beside a
     * sample, which side a clock fits, ranges and nesting.
     */
    private static final Pattern BEYOND_THE_FILE = Pattern.compile("^col \\d+: (?:while takes an action or a wait; "
        + "\\S+ is a query|clock=\\S+ doesn't fit|a sample is taken|a sample holds|every= takes 1t|if nests at most)");
    /** Wait's options each once, which wait-option's * allows. It only excuses an option the step does repeat. */
    private static final Pattern GIVEN_TWICE = Pattern.compile("^col \\d+: (\\S+=) given twice");
    private static final int MAX_SHOWN = 30;

    private final String grammar = GrammarFileTest.grammar();
    private final GrammarReading file = new GrammarReading(grammar);
    private final CommandRegistry registry = declaredBy(file);
    private final Parser parser = new Parser(registry);

    GrammarConformanceTest() throws IOException {
    }

    private static CommandRegistry declaredBy(GrammarReading file) {
        CommandRegistry registry = new CommandRegistry();
        List<BlockpilotApi.Registration> extensions = new ArrayList<>();
        for (String command : file.commands()) {
            int colon = command.indexOf(':');
            CommandSpec spec = spec(file, command, command.substring(colon + 1));
            if (colon < 0) {
                registry.register(spec);
            } else {
                extensions.add(new BlockpilotApi.Registration(command.substring(0, colon), "grammar file", spec));
            }
        }
        registry.addExtensions(extensions);
        return registry;
    }

    private static CommandSpec spec(GrammarReading file, String command, String name) {
        List<Node> options = file.options(command);
        boolean action = file.isAction(command);
        CommandSpec spec = action ? CommandSpec.action(name, ACTION) : CommandSpec.query(name, QUERY);
        for (Node form : file.forms(command)) {
            AtomicInteger slots = new AtomicInteger();
            spec.form(declared -> declare(form, declared, slots));
        }
        options.forEach(option -> declareOption(spec, option, action));
        return spec;
    }

    private static void declare(Node node, Form form, AtomicInteger slots) {
        switch (node) {
            case Sequence sequence -> sequence.items().forEach(item -> declare(item, form, slots));
            // The file doesn't give a repeated group's limit, only that it repeats.
            case Repeat(Sequence group, char operator) when operator == '*' ->
                form.repeated(Integer.MAX_VALUE, inner -> declare(group, inner, slots));
            case Repeat(Sequence group, char operator) -> form.optional(inner -> declare(group, inner, slots));
            case Repeat repeat -> declareSlot(repeat.item(), true, form, "slot" + slots.incrementAndGet());
            default -> declareSlot(node, false, form, "slot" + slots.incrementAndGet());
        }
    }

    private static void declareSlot(Node node, boolean optional, Form form, String name) {
        List<String> words = words(node);
        List<ArgType> types = types(node);
        if (types.isEmpty()) {
            String[] keywords = words.toArray(String[]::new);
            if (optional) {
                form.optionalKeyword(name, keywords);
            } else {
                form.keyword(name, keywords);
            }
        } else if (optional) {
            form.optionalArg(name, words, types.get(0), rest(types));
        } else {
            form.arg(name, words, types.get(0), rest(types));
        }
    }

    private static void declareOption(CommandSpec spec, Node option, boolean action) {
        String start = GrammarReading.optionStart(option);
        if (start.equals("full")) {
            spec.diffable();
            return;
        }
        if (start.endsWith(":")) {
            spec.option(start + "*", "");
            return;
        }
        String name = start.substring(0, start.length() - 1);
        if (name.equals("timeout") && action) {
            return;
        }
        Node value = ((Sequence) option).items().get(1);
        if (value instanceof Reference reference && reference.name().equals("option-value")) {
            spec.option(name, "");
            return;
        }
        List<String> words = words(value);
        List<ArgType> types = types(value);
        if (words.isEmpty()) {
            spec.option(name, "", types.get(0), rest(types));
        } else {
            spec.option(name, "", words, types.toArray(ArgType[]::new));
        }
    }

    private static List<Node> choices(Node node) {
        return node instanceof Choice choice ? choice.alternatives() : List.of(node);
    }

    private static List<String> words(Node node) {
        return choices(node).stream().filter(Literal.class::isInstance).map(choice -> ((Literal) choice).text())
            .toList();
    }

    private static List<ArgType> types(Node node) {
        return choices(node).stream().filter(Reference.class::isInstance)
            .map(choice -> Arrays.stream(ArgType.values())
                .filter(type -> type.production().equals(((Reference) choice).name())).findFirst().orElseThrow())
            .toList();
    }

    private static ArgType[] rest(List<ArgType> types) {
        return types.subList(1, types.size()).toArray(ArgType[]::new);
    }

    /** Null when the parser and the reading agree on the script. */
    private String disagreement(GrammarReading reading, String script) {
        boolean allowed = reading.accepts(script);
        List<String> errors = errors(script);
        if (allowed == errors.isEmpty()) {
            return null;
        }
        String shown = script.replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
        if (!allowed) {
            return "the parser takes `" + shown + "`";
        }
        return errors.stream().filter(error -> !beyondTheFile(error, script)).findFirst()
            .map(error -> "the parser refuses `" + shown + "`: " + error).orElse(null);
    }

    private static boolean beyondTheFile(String error, String script) {
        Matcher twice = GIVEN_TWICE.matcher(error);
        if (twice.find()) {
            String option = Pattern.quote(twice.group(1));
            return Pattern.compile("[ \t]" + option + "[^;\n\r]*[ \t]" + option).matcher(script).find();
        }
        return BEYOND_THE_FILE.matcher(error).find();
    }

    private List<String> errors(String script) {
        return parser.script(script).stream().map(Parser.Line::error).filter(Objects::nonNull).toList();
    }

    private void compare(String script, List<String> disagreements) {
        String disagreement = disagreement(file, script);
        if (disagreement != null) {
            disagreements.add(disagreement);
        }
    }

    private static void assertNoDisagreements(List<String> disagreements) {
        assertEquals(List.of(), disagreements.stream().distinct().limit(MAX_SHOWN).toList(),
            disagreements.size() + " scripts on which the parser and docs/grammar.ebnf disagree");
    }

    @Test
    void theRebuiltDeclarationsGenerateTheFile() {
        List<String> lines = grammar.lines().toList();
        List<String> commands = lines.subList(lines.indexOf(Grammar.COMMANDS_COMMENT) + 1, lines.size()).stream()
            .filter(line -> !line.isEmpty()).toList();
        List<String> generated = new ArrayList<>();
        registry.all().forEach(entry -> generated.addAll(Grammar.command(entry)));
        assertEquals(commands, generated);
    }

    @Test
    void theParserAndTheFileAgreeOnExamples() {
        List<String> taken = List.of("mine timeout=90 #logs x6", "mine #logs x6 timeout=90", "chat a=b", "click w3",
            "click 120,40", "click s5 shift", "mouse move 30 -10 4t", "wait 20t", "wait idle",
            "create world w1 rule:doDaylightCycle=false rule:keepInventory=true", "status; inv", "; ;\n\r status ;",
            "wait status until ok", "wait status until hp<5 and id==air stable=2t timeout=30 clock=client",
            "assert block ~0,-1,0 id==air", "sample status every 1t x5", "sample status and inv every 5t while mine",
            "?mine stone; status", "if status hp<10 then ?mine stone", "if status ok then if status ok then chat ok",
            "chat until then ok", "find stone[axis=x]", "goto 1,64,1 near=2", "sable:list side=client");
        List<String> refused = List.of("mouse move 30 up", "turn 90 20 10", "mine #logs timeout=9 timeout=90",
            "goto 1,64,1 near=abc", "? frobnicate", "frobnicate", "assert block ~0,-1,0 id=air", "wait 5", "wait",
            "wait status id==air", "if status hp<10 mine", "if status hp<10 then", "status full", "chat",
            "wait status until ok then", "sample status every 1t", "sample status every 1t clock=client x5");
        taken.forEach(script -> {
            assertTrue(file.accepts(script), script);
            assertEquals(List.of(), errors(script), script);
        });
        refused.forEach(script -> {
            assertFalse(file.accepts(script), script);
            assertFalse(errors(script).isEmpty(), script);
        });
    }

    @Test
    void onlyTheParserKeepsWaitsOptionsToOneEach() {
        String twice = "wait status until ok every=1t every=2t";
        assertTrue(file.accepts(twice));
        assertTrue(errors(twice).get(0).startsWith("col 31: every= given twice"), errors(twice).get(0));
    }

    @Test
    void onlyARealRepeatIsExcusedAsGivenTwice() {
        assertTrue(beyondTheFile("col 31: every= given twice", "wait status until ok every=1t every=2t"));
        assertFalse(beyondTheFile("col 8: since= given twice", "sounds since=5t"));
        assertFalse(beyondTheFile("col 8: since= given twice", "sounds since=5t; sounds since=6t"));
    }

    @Test
    void everyCommandsCallsAreTakenOrRefusedAsItsProductionsSay() {
        Sentences sentences = new Sentences(file, grammar, new Random(1703));
        List<String> disagreements = new ArrayList<>();
        for (String command : file.commands()) {
            int taken = 0;
            int refused = 0;
            for (int run = 0; run < 300; run++) {
                String call = sentences.call(command);
                for (String script : List.of(call, sentences.broken(call))) {
                    if (file.accepts(script)) {
                        taken++;
                    } else {
                        refused++;
                    }
                    compare(script, disagreements);
                }
            }
            assertTrue(taken > 50, command + " has too few valid calls: " + taken);
            assertTrue(refused > 50, command + " has too few broken calls: " + refused);
        }
        assertNoDisagreements(disagreements);
    }

    @Test
    void scriptsAreTakenOrRefusedAsTheCoreSays() {
        Sentences sentences = new Sentences(file, grammar, new Random(1703));
        List<String> disagreements = new ArrayList<>();
        Set<Class<?>> taken = new HashSet<>();
        for (int run = 0; run < 15_000; run++) {
            String script = sentences.script();
            for (String candidate : List.of(script, sentences.broken(script))) {
                compare(candidate, disagreements);
                parser.script(candidate).stream().map(Parser.Line::statement).filter(Objects::nonNull)
                    .forEach(statement -> taken.add(statement.getClass()));
            }
        }
        assertNoDisagreements(disagreements);
        assertEquals(Set.of(Statement.Call.class, Statement.WaitTicks.class, Statement.WaitIdle.class,
            Statement.WaitUntil.class, Statement.Sample.class, Statement.Assert.class, Statement.If.class), taken);
    }

    @Test
    void aFileThatDriftedFromTheParserIsCaught() {
        Sentences sentences = new Sentences(file, grammar, new Random(1703));
        List<String> scripts = new ArrayList<>();
        for (int run = 0; run < 4_000; run++) {
            scripts.add(sentences.script());
        }
        for (String command : file.commands()) {
            for (int run = 0; run < 40; run++) {
                scripts.add(sentences.call(command));
            }
        }
        List<List<String>> drifts = List.of(
            List.of("blank+ \"until\" blank+", "blank+ \"till\" blank+"),
            List.of("blank+ ( \"and\" blank+ )? predicate", "blank+ predicate"),
            List.of("step ::= blank* ( \"?\"? statement", "step ::= blank* ( statement"),
            List.of("duration ::= digits \"t\" | seconds \"s\"", "duration ::= digits \"t\""),
            List.of("block-command ::= \"block\" position \"light\"?", "block-command ::= \"block\" position"),
            List.of("goto-option ::= \"near=\" number | ", "goto-option ::= "),
            List.of("look-command ::= \"look\" integer?", "look-command ::= \"look\" integer"));
        for (List<String> drift : drifts) {
            assertTrue(grammar.contains(drift.get(0)), drift.get(0));
            GrammarReading drifted = new GrammarReading(grammar.replace(drift.get(0), drift.get(1)));
            assertTrue(scripts.stream().anyMatch(script -> disagreement(drifted, script) != null), drift.get(1));
        }
    }

    /** Derives sentences from a reading's productions, and breaks them a little. */
    private static final class Sentences {
        private static final Pattern KEYWORD = Pattern.compile("\"([a-z][a-z0-9_]*)\"");
        /** Besides what tokens are made of: characters only a blank's definition keeps from being one. */
        private static final String ALPHABET = "abcdefstwxz0123456789AZ_~,.-:#*/=<>!$&|[]\\"
            + "\f\u000B\u0085\u00A0\u2028\u3000";
        private static final List<String> SHAPES = List.of("position", "point", "entity", "widget", "menu-slot",
            "uuid", "integer", "count", "duration", "seconds", "id", "tag", "pattern", "block-state", "word", "test",
            "test-shape", "option", "clock", "wait-option");
        private final GrammarReading file;
        private final Random random;
        private final List<String> keywords;
        private final List<String> coreKeywords;
        private final List<String> commands;
        private final List<String> queries;

        Sentences(GrammarReading file, String grammar, Random random) {
            this.file = file;
            this.random = random;
            this.keywords = keywords(grammar);
            this.coreKeywords = keywords(grammar.substring(0, grammar.indexOf(Grammar.COMMANDS_COMMENT)));
            this.commands = file.commands();
            this.queries = commands.stream().filter(command -> !file.isAction(command)).toList();
        }

        private static List<String> keywords(String grammar) {
            return KEYWORD.matcher(grammar).results().map(result -> result.group(1)).distinct().sorted().toList();
        }

        String script() {
            return derive(new Reference("script"));
        }

        /** A call of the command, with some of its options among its items. */
        String call(String command) {
            List<String> tokens = new ArrayList<>();
            items(pick(file.forms(command)), tokens);
            for (Node option : file.options(command)) {
                if (random.nextInt(3) == 0) {
                    tokens.add(random.nextInt(tokens.size() + 1), derive(option));
                }
            }
            tokens.add(0, command);
            return String.join(" ", tokens);
        }

        String broken(String sentence) {
            int kind = random.nextInt(8);
            if (kind >= 5) {
                return brokenCharacter(sentence, kind);
            }
            List<String> tokens = new ArrayList<>(List.of(sentence.split("[ \t]+", -1)));
            int at = random.nextInt(tokens.size());
            switch (kind) {
                case 0 -> tokens.remove(at);
                case 1 -> tokens.add(at, tokens.get(at));
                case 2 -> Collections.swap(tokens, at, random.nextInt(tokens.size()));
                case 3 -> tokens.set(at, piece());
                default -> tokens.add(at, piece());
            }
            return String.join(" ", tokens);
        }

        private String brokenCharacter(String sentence, int kind) {
            StringBuilder broken = new StringBuilder(sentence);
            int at = random.nextInt(broken.length() + 1);
            char character = ALPHABET.charAt(random.nextInt(ALPHABET.length()));
            if (kind == 5 || at == broken.length()) {
                broken.insert(at, character);
            } else if (kind == 6) {
                broken.deleteCharAt(at);
            } else {
                broken.setCharAt(at, character);
            }
            return broken.toString();
        }

        private void items(Node node, List<String> tokens) {
            switch (node) {
                case Sequence sequence -> sequence.items().forEach(item -> items(item, tokens));
                case Repeat repeat -> {
                    if (random.nextBoolean()) {
                        items(repeat.item(), tokens);
                    }
                }
                default -> tokens.add(derive(node));
            }
        }

        private String derive(Node node) {
            return switch (node) {
                case Literal literal -> literal.text();
                case CharClass chars -> String.valueOf(character(chars));
                case Sequence sequence -> sequence.items().stream().map(this::derive).collect(Collectors.joining());
                case Choice choice -> derive(pick(choice.alternatives()));
                case Repeat repeat -> repeated(repeat);
                case Except except -> outside(except);
                case Reference reference -> switch (reference.name()) {
                    case "call" -> call(pick(commands));
                    case "query", "sample-query" -> call(pick(queries));
                    case "blank" -> random.nextInt(8) == 0 ? "\t" : " ";
                    case "token" -> random.nextBoolean() ? piece() : derive(file.production("token"));
                    default -> derive(file.production(reference.name()));
                };
            };
        }

        private String repeated(Repeat repeat) {
            int count = switch (repeat.operator()) {
                case '?' -> random.nextInt(2);
                case '+' -> 1 + random.nextInt(4) / 3;
                default -> random.nextInt(3);
            };
            StringBuilder repeated = new StringBuilder();
            for (int index = 0; index < count; index++) {
                repeated.append(derive(repeat.item()));
            }
            return repeated.toString();
        }

        private char character(CharClass chars) {
            while (true) {
                char character = ALPHABET.charAt(random.nextInt(ALPHABET.length()));
                if (chars.contains(character)) {
                    return character;
                }
            }
        }

        private String outside(Except except) {
            while (true) {
                String derived = derive(except.item());
                if (!file.whole(except.excluded(), derived)) {
                    return derived;
                }
            }
        }

        /**
         * A token that means something to the grammar, unlike most random ones. The core's few keywords get a share of
         * their own: they end queries and join predicates, and among all the commands' words they would hardly appear.
         */
        private String piece() {
            return switch (random.nextInt(3)) {
                case 0 -> pick(coreKeywords);
                case 1 -> pick(keywords);
                default -> derive(new Reference(pick(SHAPES)));
            };
        }

        private <T> T pick(List<T> choices) {
            return choices.get(random.nextInt(choices.size()));
        }
    }
}

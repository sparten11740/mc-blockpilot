package dev.blockpilot.protocol;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A grammar file read the way its header says, sharing no code with Parser or Lexicon, so that tests can hold them to
 * it: alternatives in order, repetition that never gives back, whole tokens, and a call's tokens against its command's
 * forms. Two rules come from the spec, as the productions don't state them: a token with a declared option's name is
 * that option, even where text could take it, and a query is a read-only command, which the actions' timeout= tells.
 */
final class GrammarReading {
    sealed interface Node {
    }

    record Literal(String text) implements Node {
    }

    record CharClass(Pattern pattern) implements Node {
        boolean contains(char character) {
            return pattern.matcher(String.valueOf(character)).matches();
        }
    }

    record Reference(String name) implements Node {
    }

    record Sequence(List<Node> items) implements Node {
    }

    record Choice(List<Node> alternatives) implements Node {
    }

    record Repeat(Node item, char operator) implements Node {
    }

    record Except(Node item, Node excluded) implements Node {
    }

    private static final int FAILED = -1;
    /** The core's comment above call: the tokens are matched against the command's forms. A query is a call too. */
    private static final Set<String> CALLS = Set.of("call", "query", "sample-query");
    private static final String CALL = "call";
    private static final Node SCRIPT = new Reference("script");
    private static final Node TOKEN = new Reference("token");
    private static final Node OPTION = new Reference("option");
    private static final String COMMAND = "-command";

    private final Map<String, Node> productions = new LinkedHashMap<>();
    /** The productions that span tokens; every other one is a production for a token. */
    private final Set<String> structural = new HashSet<>(Set.of("blank", "separator"));

    GrammarReading(String grammar) {
        Map<String, List<String>> bodies = GrammarFileTest.productions(grammar);
        bodies.forEach((name, body) -> productions.put(name, new Reader(body.get(0)).choice()));
        boolean grew = true;
        while (grew) {
            grew = false;
            for (var production : bodies.entrySet()) {
                if (GrammarFileTest.references(production.getValue().get(0)).stream().anyMatch(structural::contains)) {
                    grew |= structural.add(production.getKey());
                }
            }
        }
    }

    boolean accepts(String script) {
        return match(SCRIPT, script, 0, false) == script.length();
    }

    Node production(String name) {
        return productions.get(name);
    }

    /** The commands by the names they are called by. */
    List<String> commands() {
        return productions.keySet().stream().filter(name -> name.endsWith(COMMAND))
            .map(name -> name.substring(0, name.length() - COMMAND.length()).replace('.', ':')).toList();
    }

    /** The command's forms: the alternatives of its production's body, or the body when it has none. */
    List<Node> forms(String command) {
        Node declaration = productions.get(command.replace(':', '.') + COMMAND);
        List<Node> items = declaration instanceof Sequence sequence
            ? sequence.items().subList(1, sequence.items().size()) : List.of();
        if (items.size() == 1 && items.get(0) instanceof Choice choice) {
            return choice.alternatives();
        }
        if (items.size() == 1 && items.get(0) instanceof Repeat(Choice choice, char operator)) {
            List<Node> forms = new ArrayList<>(choice.alternatives());
            forms.add(new Sequence(List.of()));
            return forms;
        }
        return List.of(new Sequence(items));
    }

    List<Node> options(String command) {
        Node options = productions.get(command.replace(':', '.') + "-option");
        if (options == null) {
            return List.of();
        }
        return options instanceof Choice choice ? choice.alternatives() : List.of(options);
    }

    boolean isAction(String command) {
        return options(command).stream().anyMatch(option -> optionStart(option).equals("timeout="));
    }

    /** How an option alternative starts: {@code full}, {@code near=} or, for a family, {@code rule:}. */
    static String optionStart(Node option) {
        return option instanceof Sequence sequence ? ((Literal) sequence.items().get(0)).text()
            : ((Literal) option).text();
    }

    /** {@code inToken}: within one token, where a production is a plain sequence of characters. */
    private int match(Node node, String text, int at, boolean inToken) {
        return switch (node) {
            case Literal literal -> text.startsWith(literal.text(), at) ? at + literal.text().length() : FAILED;
            case CharClass chars -> at < text.length() && chars.contains(text.charAt(at)) ? at + 1 : FAILED;
            case Sequence sequence -> {
                int end = at;
                for (Node item : sequence.items()) {
                    end = match(item, text, end, inToken);
                    if (end == FAILED) {
                        break;
                    }
                }
                yield end;
            }
            case Choice choice -> {
                for (Node alternative : choice.alternatives()) {
                    int end = match(alternative, text, at, inToken);
                    if (end != FAILED) {
                        yield end;
                    }
                }
                yield FAILED;
            }
            case Repeat repeat -> repeated(repeat, text, at, inToken);
            case Except except -> {
                if (!inToken) {
                    yield nextToken(except, text, at);
                }
                int end = match(except.item(), text, at, true);
                yield end != FAILED && whole(except.excluded(), text.substring(at, end)) ? FAILED : end;
            }
            case Reference reference -> {
                if (!inToken && !structural.contains(reference.name())) {
                    yield nextToken(reference, text, at);
                }
                int end = match(productions.get(reference.name()), text, at, inToken);
                boolean refused = end != FAILED && CALLS.contains(reference.name())
                    && !call(text.substring(at, end), reference.name().equals(CALL));
                yield refused ? FAILED : end;
            }
        };
    }

    private int repeated(Repeat repeat, String text, int at, boolean inToken) {
        int end = match(repeat.item(), text, at, inToken);
        if (end == FAILED) {
            return repeat.operator() == '+' ? FAILED : at;
        }
        if (repeat.operator() == '?') {
            return end;
        }
        for (int next = match(repeat.item(), text, end, inToken); next != FAILED;
            next = match(repeat.item(), text, end, inToken)) {
            end = next;
        }
        return end;
    }

    private int nextToken(Node node, String text, int at) {
        int end = match(TOKEN, text, at, true);
        return end != FAILED && whole(node, text.substring(at, end)) ? end : FAILED;
    }

    /**
     * "A production for a token matches the whole token": an alternative that matches only part of it isn't taken, so
     * {@code ok==1} is a test although it starts with {@code "ok"}.
     */
    boolean whole(Node node, String token) {
        return switch (node) {
            case Reference reference -> whole(productions.get(reference.name()), token);
            case Choice choice -> choice.alternatives().stream().anyMatch(alternative -> whole(alternative, token));
            case Except except -> whole(except.item(), token) && !whole(except.excluded(), token);
            case Sequence sequence -> {
                List<Node> items = sequence.items();
                int end = 0;
                for (Node item : items.subList(0, items.size() - 1)) {
                    end = match(item, token, end, true);
                    if (end == FAILED) {
                        yield false;
                    }
                }
                yield whole(items.get(items.size() - 1), token.substring(end));
            }
            default -> match(node, token, 0, true) == token.length();
        };
    }

    private boolean call(String call, boolean mayAct) {
        List<String> tokens = List.of(call.split("[ \t]+"));
        String command = tokens.get(0);
        return productions.containsKey(command.replace(':', '.') + COMMAND) && (mayAct || !isAction(command))
            && forms(command).stream().anyMatch(form -> new FormReading(tokens, options(command)).takes(form));
    }

    /** One form against a call's tokens: items in order, the command's options between any two, each once. */
    private final class FormReading {
        private final List<String> tokens;
        private final List<Node> options;
        private int position = 1;
        private Set<String> given = new HashSet<>();

        FormReading(List<String> tokens, List<Node> options) {
            this.tokens = tokens;
            this.options = options;
        }

        boolean takes(Node form) {
            return items(form) && options() && position == tokens.size();
        }

        private boolean items(Node node) {
            return switch (node) {
                case Sequence sequence -> sequence.items().stream().allMatch(this::items);
                case Repeat repeat -> {
                    int before = position;
                    Set<String> givenBefore = new HashSet<>(given);
                    if (!items(repeat.item())) {
                        position = before;
                        given = givenBefore;
                    }
                    yield true;
                }
                case Reference reference when reference.name().equals("text") -> {
                    if (!options() || position == tokens.size()) {
                        yield false;
                    }
                    position = tokens.size();
                    yield true;
                }
                default -> {
                    if (!options() || position == tokens.size() || !whole(node, tokens.get(position))) {
                        yield false;
                    }
                    position++;
                    yield true;
                }
            };
        }

        /** False when an option at the cursor is declared with a value it doesn't take, or was given already. */
        private boolean options() {
            while (position < tokens.size()) {
                String token = tokens.get(position);
                Node option = declared(token);
                if (option == null) {
                    return true;
                }
                int equals = token.indexOf('=');
                if (!whole(option, token) || !given.add(equals < 0 ? token : token.substring(0, equals))) {
                    return false;
                }
                position++;
            }
            return true;
        }

        private Node declared(String token) {
            boolean shaped = whole(OPTION, token);
            for (Node option : options) {
                String start = optionStart(option);
                if (start.equals("full") ? token.equals(start) : shaped && token.startsWith(start)) {
                    return option;
                }
            }
            return null;
        }
    }

    /** One right-hand side, in W3C EBNF. */
    private static final class Reader {
        private static final Pattern COMMENT = Pattern.compile("/\\*.*?\\*/");
        private static final Pattern NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9._-]*");
        private static final Pattern HEX = Pattern.compile("#x([0-9A-Fa-f]+)");
        private static final char END = '\0';
        private final String text;
        private int position;

        Reader(String body) {
            text = COMMENT.matcher(body).replaceAll("");
        }

        Node choice() {
            List<Node> alternatives = new ArrayList<>(List.of(sequence()));
            while (peek() == '|') {
                position++;
                alternatives.add(sequence());
            }
            return alternatives.size() == 1 ? alternatives.get(0) : new Choice(List.copyOf(alternatives));
        }

        private Node sequence() {
            List<Node> items = new ArrayList<>();
            while (peek() != END && peek() != '|' && peek() != ')') {
                items.add(except());
            }
            return items.size() == 1 ? items.get(0) : new Sequence(List.copyOf(items));
        }

        private Node except() {
            Node item = postfixed();
            if (peek() != '-') {
                return item;
            }
            position++;
            return new Except(item, postfixed());
        }

        private Node postfixed() {
            Node primary = primary();
            if (position < text.length() && "?*+".indexOf(text.charAt(position)) >= 0) {
                return new Repeat(primary, text.charAt(position++));
            }
            return primary;
        }

        private Node primary() {
            char next = peek();
            if (next == '(') {
                position++;
                Node inner = choice();
                peek();
                position++;
                return inner;
            }
            if (next == '"' || next == '\'') {
                int end = text.indexOf(next, position + 1);
                Node literal = new Literal(text.substring(position + 1, end));
                position = end + 1;
                return literal;
            }
            if (next == '[') {
                int end = text.indexOf(']', position);
                String characters = HEX.matcher(text.substring(position + 1, end)).replaceAll("\\\\x{$1}");
                position = end + 1;
                return new CharClass(Pattern.compile("[" + characters + "]"));
            }
            Matcher hex = HEX.matcher(text).region(position, text.length());
            if (hex.lookingAt()) {
                position = hex.end();
                return new Literal(Character.toString(Integer.parseInt(hex.group(1), 16)));
            }
            Matcher name = NAME.matcher(text).region(position, text.length());
            if (!name.lookingAt()) {
                throw new IllegalArgumentException("not W3C EBNF at " + text.substring(position));
            }
            position = name.end();
            return new Reference(name.group());
        }

        private char peek() {
            while (position < text.length() && Character.isWhitespace(text.charAt(position))) {
                position++;
            }
            return position < text.length() ? text.charAt(position) : END;
        }
    }
}

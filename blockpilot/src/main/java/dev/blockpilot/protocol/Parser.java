package dev.blockpilot.protocol;

import dev.blockpilot.api.ArgType;
import dev.blockpilot.api.Args;
import dev.blockpilot.api.CommandSpec;
import dev.blockpilot.api.Duration;
import dev.blockpilot.api.Form;
import dev.blockpilot.api.Lexicon;
import dev.blockpilot.api.Slot;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A recursive-descent parser for scripts, following the grammar of the {@code command-language} spec; a call's
 * arguments are matched against the forms its command declares (design D13).
 */
final class Parser {
    /** A parsed step: its statement, or the syntax error ({@code col <n>: <hint>}) that keeps it from running. */
    record Line(Statement statement, boolean soft, String error) {
    }

    static final class SyntaxError extends Exception {
        SyntaxError(int column, String message) {
            super("col " + column + ": " + message);
        }
    }

    private static final Set<String> STOP_WORDS = Set.of("until", "every", "then");
    private static final Set<String> WAIT_OPTIONS = Set.of("stable", "every", "timeout", "clock");
    private static final Set<String> SAMPLE_OPTIONS = Set.of("clock");
    private static final String PREDICATE = "a predicate (field==value, field<n or ok)";

    private final CommandRegistry registry;

    Parser(CommandRegistry registry) {
        this.registry = registry;
    }

    List<Line> script(String script) {
        List<Line> lines = new ArrayList<>();
        for (Lexer.Step step : Lexer.steps(script)) {
            Cursor cursor = new Cursor(step);
            boolean soft = false;
            try {
                soft = cursor.softMarker();
                lines.add(new Line(cursor.statement(cursor.tokens.size()), soft, null));
            } catch (SyntaxError e) {
                lines.add(new Line(null, soft, e.getMessage()));
            }
        }
        return lines;
    }

    /** Matches an example's arguments against its command's forms; the example's first word names the command. */
    static Statement.Call example(CommandRegistry.Entry entry, String example) throws SyntaxError {
        List<Lexer.Step> steps = Lexer.steps(example);
        if (steps.size() != 1) {
            throw new SyntaxError(1, "an example is one step");
        }
        Cursor cursor = new Parser(null).new Cursor(steps.get(0));
        cursor.index = 1;
        return cursor.arguments(entry, cursor.tokens.size());
    }

    /** One step's tokens and the position of the next one. */
    private final class Cursor {
        private final String source;
        private final List<Lexer.Token> tokens;
        private int index;

        Cursor(Lexer.Step step) {
            this.source = step.source();
            this.tokens = new ArrayList<>(step.tokens());
        }

        /** Takes a {@code ?} written before the statement at the cursor; true when there was one. */
        boolean softMarker() throws SyntaxError {
            Lexer.Token token = tokens.get(index);
            if (!token.text().startsWith("?")) {
                return false;
            }
            if (token.text().length() == 1) {
                throw new SyntaxError(token.column(), "a statement must follow ? directly");
            }
            tokens.set(index, new Lexer.Token(token.text().substring(1), token.column() + 1, token.offset() + 1));
            return true;
        }

        Statement statement(int end) throws SyntaxError {
            return switch (tokens.get(index).text()) {
                case "wait" -> waitStatement(end);
                case "sample" -> sample(end);
                case "assert" -> assertion(end);
                case "if" -> conditional(end);
                default -> call(end, false);
            };
        }

        private Statement.Call call(int end, boolean readOnly) throws SyntaxError {
            Lexer.Token name = tokens.get(index);
            CommandRegistry.Entry entry = registry.resolve(name.text());
            if (entry == null) {
                throw new SyntaxError(name.column(), "unknown command " + name.text() + "; try help");
            }
            if (readOnly && entry.spec().kind() != CommandSpec.Kind.QUERY) {
                throw new SyntaxError(name.column(), name.text() + " is not a read-only command");
            }
            index++;
            return arguments(entry, end);
        }

        /** The arguments up to {@code end}, matched against each form in turn. */
        Statement.Call arguments(CommandRegistry.Entry entry, int end) throws SyntaxError {
            CommandSpec spec = entry.spec();
            Mismatch best = null;
            List<String> usages = spec.usages(entry.qualifiedName());
            for (int form = 0; form < usages.size(); form++) {
                Matcher matcher = new Matcher(spec, index, end);
                try {
                    matcher.elements(spec.forms().get(form).elements());
                    matcher.finish();
                    index = end;
                    return new Statement.Call(entry,
                        new Args(matcher.values, matcher.kinds, matcher.options), matcher.full);
                } catch (Mismatch mismatch) {
                    best = Mismatch.better(best, mismatch.withUsage(usages.get(form)));
                }
            }
            throw new SyntaxError(column(best.position), best.message());
        }

        private Statement waitStatement(int end) throws SyntaxError {
            index++;
            if (index == end) {
                throw new SyntaxError(column(index), "missing a duration, idle or <query> until <predicate>");
            }
            String next = tokens.get(index).text();
            if (Lexicon.isDuration(next) || next.equals("idle")) {
                index++;
                expectEnd(end);
                return next.equals("idle") ? new Statement.WaitIdle() : new Statement.WaitTicks(Duration.parse(next));
            }
            Statement.Call query = query(end);
            expectWord("until", end);
            Condition condition = predicates(end);
            Map<String, Lexer.Token> options = options(end, WAIT_OPTIONS);
            checkClock(query, options);
            Lexer.Token timeout = options.get("timeout");
            return new Statement.WaitUntil(query, condition, ticks(options.get("stable")), ticks(options.get("every")),
                timeout == null ? null : duration(timeout, true));
        }

        private Statement sample(int end) throws SyntaxError {
            index++;
            Statement.Call query = query(end);
            expectWord("every", end);
            Duration every = Duration.parse(expectToken(ArgType.DURATION, end));
            int count = Integer.parseInt(expectToken(ArgType.COUNT, end).substring(1));
            checkClock(query, options(end, SAMPLE_OPTIONS));
            return new Statement.Sample(query, every, count);
        }

        private Statement assertion(int end) throws SyntaxError {
            index++;
            Statement.Call query = query(end);
            Condition condition = predicates(end);
            expectEnd(end);
            return new Statement.Assert(query, condition);
        }

        private Statement conditional(int end) throws SyntaxError {
            index++;
            Statement.Call query = query(end);
            Condition condition = predicates(end);
            expectWord("then", end);
            if (index == end) {
                throw new SyntaxError(column(index), "missing a statement after then");
            }
            boolean soft = softMarker();
            return new Statement.If(query, condition, statement(end), soft);
        }

        /** A read-only command and its arguments, up to the first stop word or predicate. */
        private Statement.Call query(int end) throws SyntaxError {
            if (index == end || isStopWord(tokens.get(index).text())) {
                throw expected("a query (a read-only command)", end);
            }
            int stop = index + 1;
            while (stop < end && !isStopWord(tokens.get(stop).text())) {
                stop++;
            }
            return call(stop, true);
        }

        private static boolean isStopWord(String token) {
            return STOP_WORDS.contains(token) || token.equals("ok") || Lexicon.testShape(token) != null;
        }

        /** {@code predicate ( "and"? predicate )*} */
        private Condition predicates(int end) throws SyntaxError {
            List<Lexicon.Test> tests = new ArrayList<>();
            boolean requireOk = false;
            boolean any = false;
            while (index < end) {
                String token = tokens.get(index).text();
                boolean joined = any && token.equals("and") && index + 1 < end;
                Lexer.Token predicate = tokens.get(joined ? index + 1 : index);
                Lexicon.Test test = Lexicon.testShape(predicate.text());
                if (!predicate.text().equals("ok") && test == null) {
                    break;
                }
                if (test != null && !Lexicon.isTest(predicate.text())) {
                    throw new SyntaxError(predicate.column(), test.isOrdering()
                        ? test.comparison() + " compares with a number or $field, not " + test.operand()
                        : "a value can't hold $ < > = ! & or |, got " + test.operand());
                }
                index += joined ? 2 : 1;
                requireOk |= test == null;
                if (test != null) {
                    tests.add(test);
                }
                any = true;
            }
            if (!any) {
                throw expected(PREDICATE, end);
            }
            return Condition.of(tests, requireOk);
        }

        /** The construct's own {@code name=value} options, to the end, by name. */
        private Map<String, Lexer.Token> options(int end, Set<String> allowed) throws SyntaxError {
            Map<String, Lexer.Token> options = new LinkedHashMap<>();
            for (; index < end; index++) {
                Lexer.Token token = tokens.get(index);
                int equals = Lexicon.optionEquals(token.text());
                if (equals < 0) {
                    throw new SyntaxError(token.column(), "unexpected " + token.text());
                }
                String name = token.text().substring(0, equals);
                if (!allowed.contains(name)) {
                    throw new SyntaxError(token.column(), "unknown option " + name + "= (takes "
                        + String.join("=, ", allowed.stream().sorted().toList()) + "=)");
                }
                if (options.put(name, token) != null) {
                    throw new SyntaxError(token.column(), name + "= given twice");
                }
            }
            return options;
        }

        private static String value(Lexer.Token option) {
            return option.text().substring(option.text().indexOf('=') + 1);
        }

        private void checkClock(Statement.Call query, Map<String, Lexer.Token> options) throws SyntaxError {
            Lexer.Token clock = options.get("clock");
            if (clock == null) {
                return;
            }
            if (!value(clock).equals("server") && !value(clock).equals("client")) {
                throw new SyntaxError(clock.column(), "clock= takes server or client, not " + value(clock));
            }
            if (value(clock).equals("server") && !query.entry().spec().runsOnServer()) {
                throw new SyntaxError(clock.column(), "clock=server needs a server-side query; "
                    + query.entry().qualifiedName() + " runs on the client");
            }
        }

        /** A duration option's ticks, at least 1, or 1 when it isn't given. */
        private int ticks(Lexer.Token option) throws SyntaxError {
            return option == null ? 1 : Math.max(1, duration(option, false).ticks());
        }

        /** A duration option's value; with {@code seconds}, a plain number counts as seconds. */
        private Duration duration(Lexer.Token option, boolean seconds) throws SyntaxError {
            String value = value(option);
            if (Lexicon.isDuration(value)) {
                return Duration.parse(value);
            }
            if (seconds && Lexicon.isDecimal(value) && !value.startsWith("-")) {
                return Duration.seconds(Double.parseDouble(value));
            }
            String name = option.text().substring(0, option.text().indexOf('='));
            throw new SyntaxError(option.column(), name + "= expects a duration like 20t or 3s, not " + value);
        }

        /** The token at the cursor, which must be of the type; the cursor moves past it. */
        private String expectToken(ArgType type, int end) throws SyntaxError {
            if (index == end || !type.matches(tokens.get(index).text())) {
                throw expected(type.description(), end);
            }
            return tokens.get(index++).text();
        }

        private void expectWord(String word, int end) throws SyntaxError {
            if (index == end || !tokens.get(index).text().equals(word)) {
                throw expected(word, end);
            }
            index++;
        }

        private void expectEnd(int end) throws SyntaxError {
            if (index < end) {
                throw new SyntaxError(tokens.get(index).column(), "unexpected " + tokens.get(index).text());
            }
        }

        private SyntaxError expected(String what, int end) {
            return index < end
                ? new SyntaxError(tokens.get(index).column(), what + " expected, got " + tokens.get(index).text())
                : new SyntaxError(column(index), "missing " + what);
        }

        /** The token's column, or one past the step's end. */
        private int column(int position) {
            if (position < tokens.size()) {
                return tokens.get(position).column();
            }
            Lexer.Token last = tokens.get(tokens.size() - 1);
            return last.column() + last.text().length() + 1;
        }

        /** One attempt to match a form against the tokens from {@code from} to {@code end}. */
        private final class Matcher {
            private final CommandSpec spec;
            private final int end;
            private int position;
            private final Map<String, String> values = new LinkedHashMap<>();
            private final Map<String, ArgType> kinds = new LinkedHashMap<>();
            private final Map<String, String> options = new LinkedHashMap<>();
            private boolean full;

            Matcher(CommandSpec spec, int from, int end) {
                this.spec = spec;
                this.position = from;
                this.end = end;
            }

            void elements(List<Form.Element> elements) throws Mismatch {
                for (Form.Element element : elements) {
                    switch (element) {
                        case Slot slot when slot.isText() -> text(slot);
                        case Slot slot -> slot(slot);
                        case Form.Group group -> {
                            options(false);
                            if (position < end && fits(group)) {
                                elements(group.elements());
                            }
                        }
                    }
                }
            }

            private void slot(Slot slot) throws Mismatch {
                options(false);
                ArgType type = position < end ? slot.match(tokens.get(position).text()) : null;
                if (type != null) {
                    values.put(slot.name(), tokens.get(position).text());
                    kinds.put(slot.name(), type);
                    position++;
                } else if (!slot.optional()) {
                    throw expectation(slot.expectation());
                }
            }

            private void text(Slot slot) throws Mismatch {
                options(true);
                int to = end < tokens.size() ? tokens.get(end).offset() : source.length();
                String text = position < end ? source.substring(tokens.get(position).offset(), to).strip() : "";
                if (!text.isEmpty()) {
                    values.put(slot.name(), text);
                    kinds.put(slot.name(), ArgType.TEXT);
                    position = end;
                } else if (!slot.optional()) {
                    throw expectation("<" + slot.name() + "> (text)");
                }
            }

            /** Whether the group's first part takes the next token (groups never hold text). */
            private boolean fits(Form.Group group) {
                return switch (group.elements().get(0)) {
                    case Slot slot -> slot.match(tokens.get(position).text()) != null;
                    case Form.Group inner -> fits(inner);
                };
            }

            void finish() throws Mismatch {
                options(false);
                if (spec.isDiffable() && position < end && tokens.get(position).text().equals("full")) {
                    full = true;
                    position++;
                    options(false);
                }
                if (position < end) {
                    throw new Mismatch(position, null, tokens.get(position).text(),
                        "unexpected " + tokens.get(position).text());
                }
            }

            /** Takes the options at the cursor; where text may start, only declared ones. */
            private void options(boolean textMayFollow) throws Mismatch {
                while (position < end) {
                    String token = tokens.get(position).text();
                    int equals = Lexicon.optionEquals(token);
                    if (equals < 0) {
                        return;
                    }
                    String name = token.substring(0, equals);
                    if (!declared(name)) {
                        if (textMayFollow) {
                            return;
                        }
                        throw new Mismatch(position, null, token, "unknown option " + name + "= ("
                            + (takesOptions() ? "takes " + String.join("=, ", optionNames()) + "=" : "takes none")
                            + ")");
                    }
                    if (options.put(name, token.substring(equals + 1)) != null) {
                        throw new Mismatch(position, null, token, name + "= given twice");
                    }
                    position++;
                }
            }

            private boolean declared(String name) {
                int colon = name.indexOf(':');
                return spec.options().containsKey(name)
                    || colon > 0 && spec.options().containsKey(name.substring(0, colon) + ":*")
                    || isAction() && name.equals("timeout");
            }

            private boolean isAction() {
                return spec.kind() == CommandSpec.Kind.ACTION;
            }

            private boolean takesOptions() {
                return !spec.options().isEmpty() || isAction();
            }

            private List<String> optionNames() {
                List<String> names = new ArrayList<>(spec.options().keySet());
                if (isAction()) {
                    names.add("timeout");
                }
                return names;
            }

            private Mismatch expectation(String expected) {
                return new Mismatch(position, expected, position < end ? tokens.get(position).text() : null, null);
            }
        }
    }

    /**
     * Why a form didn't match, at which token: either something expected there (merged across forms failing at the
     * same token) or a message of its own.
     */
    private static final class Mismatch extends Exception {
        private final int position;
        private final String expected;
        private final String got;
        private final String own;
        private final List<String> usages = new ArrayList<>();

        Mismatch(int position, String expected, String got, String own) {
            super(null, null, false, false);
            this.position = position;
            this.expected = expected;
            this.got = got;
            this.own = own;
        }

        Mismatch withUsage(String usage) {
            usages.add(usage);
            return this;
        }

        /** The one that got further; at the same token, expectations (or identical messages) are merged. */
        static Mismatch better(Mismatch best, Mismatch candidate) {
            if (best == null || candidate.position > best.position) {
                return candidate;
            }
            if (candidate.position < best.position) {
                return best;
            }
            Mismatch combined;
            if (best.expected != null && candidate.expected != null) {
                String merged = best.expected.equals(candidate.expected) ? best.expected
                    : best.expected + " or " + candidate.expected;
                combined = new Mismatch(best.position, merged, best.got, null);
            } else if (best.own != null && best.own.equals(candidate.own)) {
                combined = new Mismatch(best.position, null, best.got, best.own);
            } else {
                return best;
            }
            combined.usages.addAll(best.usages);
            combined.usages.addAll(candidate.usages);
            return combined;
        }

        String message() {
            String what = own != null ? own : got == null ? "missing " + expected : expected + " expected, got " + got;
            return what + "; " + String.join(" | ", usages);
        }
    }
}

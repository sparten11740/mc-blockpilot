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
import java.util.LinkedHashSet;
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

    /** How many {@code if}s a step may hold, each in the one before's {@code then}. */
    static final int MAX_NESTING = 16;
    static final int MAX_SAMPLES = 1000;

    private static final Set<String> STOP_WORDS = Set.of("ok", "until", "every", "then");
    private static final List<String> COMPARISONS = List.of("==", "!=", "<=", ">=", "<", ">");
    private static final String PREDICATE = "a predicate (field==value, field<n or ok)";
    private static final CommandSpec.Option CLOCK = option("clock", List.of("server", "client"));
    private static final Map<String, CommandSpec.Option> WAIT_OPTIONS = options(
        option("stable", List.of(), ArgType.DURATION), option("every", List.of(), ArgType.DURATION),
        CommandSpec.TIMEOUT, CLOCK);
    private static final Map<String, CommandSpec.Option> SAMPLE_OPTIONS = options(CLOCK);

    private final CommandRegistry registry;

    Parser(CommandRegistry registry) {
        this.registry = registry;
    }

    private static CommandSpec.Option option(String name, List<String> words, ArgType... types) {
        List<ArgType> all = new ArrayList<>(words.isEmpty() ? List.of() : List.of(ArgType.KEYWORD));
        all.addAll(List.of(types));
        return new CommandSpec.Option(name, "", new Slot("value", all, words, false));
    }

    private static Map<String, CommandSpec.Option> options(CommandSpec.Option... options) {
        Map<String, CommandSpec.Option> byName = new LinkedHashMap<>();
        for (CommandSpec.Option option : options) {
            byName.put(option.name(), option);
        }
        return byName;
    }

    List<Line> script(String script) {
        List<Line> lines = new ArrayList<>();
        for (Lexer.Step step : Lexer.steps(script)) {
            Cursor cursor = new Cursor(step);
            boolean soft = step.tokens().get(0).text().startsWith("?");
            try {
                cursor.softMarker();
                lines.add(new Line(cursor.statement(cursor.tokens.size()), soft, null));
            } catch (SyntaxError e) {
                lines.add(new Line(null, soft, e.getMessage()));
            }
        }
        return lines;
    }

    /** Matches an example against its command's forms; the example starts with the name the command is called by. */
    static Statement.Call example(CommandRegistry.Entry entry, String example) throws SyntaxError {
        List<Lexer.Step> steps = Lexer.steps(example);
        if (steps.size() != 1) {
            throw new SyntaxError(1, "an example is one step");
        }
        Cursor cursor = new Parser(null).new Cursor(steps.get(0));
        String name = cursor.text(0);
        if (!name.equals(entry.qualifiedName())) {
            throw new SyntaxError(1, "an example starts with " + entry.qualifiedName() + ", not " + name);
        }
        cursor.index = 1;
        return cursor.arguments(entry, cursor.tokens.size());
    }

    /** One step's tokens and the position of the next one. */
    private final class Cursor {
        private final String source;
        private final List<Lexer.Token> tokens;
        private final int width;
        private int index;
        private int nesting;
        /** The construct being read, whose usage its own errors carry. */
        private Construct construct;

        Cursor(Lexer.Step step) {
            this.source = step.source();
            this.tokens = new ArrayList<>(step.tokens());
            this.width = step.width();
        }

        private String text(int position) {
            return tokens.get(position).text();
        }

        /** Takes a {@code ?} written before the statement at the cursor. */
        void softMarker() throws SyntaxError {
            Lexer.Token token = tokens.get(index);
            if (!token.text().startsWith("?")) {
                return;
            }
            if (token.text().length() == 1) {
                throw new SyntaxError(token.column(), "a statement must follow ? directly");
            }
            tokens.set(index, new Lexer.Token(token.text().substring(1), token.column() + 1, token.offset() + 1));
        }

        Statement statement(int end) throws SyntaxError {
            Construct outer = construct;
            construct = Construct.named(text(index));
            try {
                return switch (text(index)) {
                    case "wait" -> waitStatement(end);
                    case "sample" -> sample(end);
                    case "assert" -> assertion(end);
                    case "if" -> conditional(end);
                    default -> call(end, false);
                };
            } finally {
                construct = outer;
            }
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
                    matcher.elements(spec.forms().get(form).elements(), false);
                    matcher.finish();
                    index = end;
                    return new Statement.Call(entry, new Args(matcher.arguments, matcher.options), matcher.full);
                } catch (Mismatch mismatch) {
                    best = Mismatch.better(best, mismatch.withUsage(usages.get(form)));
                }
            }
            throw new SyntaxError(column(best.position), best.message());
        }

        private Statement waitStatement(int end) throws SyntaxError {
            index++;
            if (index == end) {
                throw error(column(index), "missing a duration, idle or <query> until <predicate>");
            }
            String next = text(index);
            if (Lexicon.isDuration(next) || next.equals("idle")) {
                index++;
                expectEnd(end);
                return next.equals("idle") ? new Statement.WaitIdle() : new Statement.WaitTicks(Duration.parse(next));
            }
            if (Lexicon.isSeconds(next)) {
                throw error(column(index), "a duration needs a unit: " + next + "t or " + next + "s");
            }
            if (registry.resolve(next) == null && "0123456789.-".indexOf(next.charAt(0)) >= 0) {
                throw error(column(index), "a duration like 20t or 3s expected, got " + next);
            }
            Statement.Call query = query(end, WAIT_OPTIONS);
            if (index < end && WAIT_OPTIONS.containsKey(text(index))) {
                throw optionWrittenAsWord(end);
            }
            expectWord("until", end);
            Condition condition = predicates(end, WAIT_OPTIONS);
            Map<String, Lexer.Token> options = options(end, WAIT_OPTIONS);
            checkClock(query, options);
            Lexer.Token every = options.get("every");
            if (every != null && duration(every).ticks() < 1) {
                throw error(every.column(), "every= takes 1t or more");
            }
            Lexer.Token stable = options.get("stable");
            Lexer.Token timeout = options.get("timeout");
            return new Statement.WaitUntil(query, condition, stable == null ? 1 : Math.max(1, duration(stable).ticks()),
                every == null ? 1 : duration(every).ticks(), timeout == null ? null : duration(timeout));
        }

        private Statement sample(int end) throws SyntaxError {
            index++;
            Statement.Call query = query(end, SAMPLE_OPTIONS);
            expectWord("every", end);
            int interval = column(index);
            Duration every = Duration.parse(expectToken(ArgType.DURATION, end));
            if (every.ticks() < 1) {
                throw error(interval, "a sample is taken every 1t or more");
            }
            int beside = index;
            while (beside < end && !text(beside).equals("while")) {
                beside++;
            }
            int count = MAX_SAMPLES;
            if (index < beside && ArgType.COUNT.matches(text(index))) {
                int countColumn = column(index);
                String times = text(index++);
                count = Integer.parseInt(times.substring(1));
                if (count < 1 || count > MAX_SAMPLES) {
                    throw error(countColumn, "a sample is taken x1 to x" + MAX_SAMPLES + " times, not " + times);
                }
            } else if (beside == end) {
                throw expected("a count x<n> or while <action|wait>", end);
            }
            checkClock(query, options(beside, SAMPLE_OPTIONS));
            if (beside == end) {
                return new Statement.Sample(query, every, count, null);
            }
            index++;
            return new Statement.Sample(query, every, count, beside(end));
        }

        /** The action or wait after {@code while}. */
        private Statement beside(int end) throws SyntaxError {
            if (index == end) {
                throw error(column(index), "missing an action or wait after while");
            }
            int column = column(index);
            if (text(index).equals("wait")) {
                return waitStatement(end);
            }
            if (Construct.named(text(index)) != null) {
                throw error(column, "while takes an action or a wait, not " + text(index));
            }
            Statement.Call action = call(end, false);
            if (action.entry().spec().kind() != CommandSpec.Kind.ACTION) {
                throw error(column, "while takes an action or a wait; " + action.entry().qualifiedName()
                    + " is a query");
            }
            return action;
        }

        private Statement assertion(int end) throws SyntaxError {
            index++;
            Statement.Call query = query(end, Map.of());
            Condition condition = predicates(end, Map.of());
            expectEnd(end);
            return new Statement.Assert(query, condition);
        }

        private Statement conditional(int end) throws SyntaxError {
            if (++nesting > MAX_NESTING) {
                throw error(column(index), "if nests at most " + MAX_NESTING + " deep");
            }
            index++;
            Statement.Call query = query(end, Map.of());
            Condition condition = predicates(end, Map.of());
            expectWord("then", end);
            if (index == end) {
                throw error(column(index), "missing a statement after then");
            }
            boolean soft = text(index).startsWith("?");
            softMarker();
            return new Statement.If(query, condition, statement(end), soft);
        }

        /**
         * A read-only command and its arguments, up to the first stop word or predicate. When it doesn't parse, a
         * construct option or a predicate written into it is the likelier mistake.
         */
        private Statement.Call query(int end, Map<String, CommandSpec.Option> constructOptions) throws SyntaxError {
            if (index == end || isStop(text(index))) {
                throw expected("a query (a read-only command)", end);
            }
            if (Construct.named(text(index)) != null) {
                throw error(column(index), text(index) + " can't be a query; constructs run only as statements");
            }
            int from = index;
            int stop = index + 1;
            while (stop < end && !isStop(text(stop))) {
                stop++;
            }
            try {
                return call(stop, true);
            } catch (SyntaxError e) {
                CommandRegistry.Entry entry = registry.resolve(text(from));
                SyntaxError hint = entry == null ? null : queryHint(entry.spec(), from + 1, stop, constructOptions);
                throw hint == null ? e : hint;
            }
        }

        private SyntaxError queryHint(CommandSpec query, int from, int stop,
            Map<String, CommandSpec.Option> constructOptions) {
            for (int position = from; position < stop; position++) {
                int equals = Lexicon.optionEquals(text(position));
                String name = equals < 0 ? null : text(position).substring(0, equals);
                boolean declared = name != null && query.findOption(name) != null;
                if (name != null && !declared && constructOptions.containsKey(name)) {
                    return error(column(position), name + "= belongs to " + construct.name() + ", after "
                        + (construct.name().equals("wait") ? "the predicates" : "the count"));
                }
                if (!declared && "every".equals(name) && construct.name().equals("sample")) {
                    return error(column(position), "sample takes every and its duration as words: every "
                        + text(position).substring(equals + 1));
                }
                String hint = declared ? null : predicateHint(position, stop);
                if (hint != null) {
                    return error(column(position), hint);
                }
            }
            return null;
        }

        private static boolean isStop(String token) {
            return STOP_WORDS.contains(token) || Lexicon.testShape(token) != null;
        }

        /**
         * A hint for a token meant as a predicate: written with a single {@code =} or with blanks around its
         * comparison; null when it doesn't look like one.
         */
        private String predicateHint(int position, int end) {
            String token = text(position);
            int equals = token.indexOf('=');
            if (equals > 0 && equals + 1 < token.length() && token.charAt(equals + 1) != '='
                && Lexicon.isFieldPath(token.substring(0, equals))) {
                return "predicates compare with ==: " + token.substring(0, equals) + "=" + token.substring(equals);
            }
            boolean previous = Lexicon.isFieldPath(text(position - 1));
            boolean following = position + 1 < end;
            String field = token.replaceFirst("[<>=!]+$", "");
            String joined = null;
            if (COMPARISONS.contains(token) && previous && following) {
                joined = text(position - 1) + token + text(position + 1);
            } else if (previous && COMPARISONS.stream().anyMatch(comparison -> token.startsWith(comparison)
                && token.length() > comparison.length())) {
                joined = text(position - 1) + token;
            } else if (following && !field.isEmpty() && COMPARISONS.contains(token.substring(field.length()))
                && Lexicon.isFieldPath(field)) {
                joined = token + text(position + 1);
            }
            return joined == null ? null : "a predicate is one token, without blanks: " + joined;
        }

        /**
         * {@code predicate ( "and"? predicate )*}. {@code options} are the construct's, which may follow; for a
         * construct without options, a token after the predicates that looks like one gets a hint.
         */
        private Condition predicates(int end, Map<String, CommandSpec.Option> options) throws SyntaxError {
            List<Lexicon.Test> tests = new ArrayList<>();
            boolean requireOk = false;
            while (index < end) {
                boolean joined = (!tests.isEmpty() || requireOk) && text(index).equals("and");
                if (joined && ++index == end) {
                    throw expected(PREDICATE, end);
                }
                Lexer.Token predicate = tokens.get(index);
                Lexicon.Test test = Lexicon.testShape(predicate.text());
                if (!predicate.text().equals("ok") && test == null) {
                    if (joined || tests.isEmpty() && !requireOk) {
                        throw notAPredicate(end);
                    }
                    boolean option = Lexicon.optionEquals(predicate.text()) > 0 && !options.isEmpty();
                    String hint = option ? null : likelyPredicate(end);
                    if (hint != null) {
                        throw error(predicate.column(), hint);
                    }
                    break;
                }
                if (test != null && !Lexicon.isTest(predicate.text())) {
                    throw error(predicate.column(), test.isOrdering()
                        ? test.comparison() + " compares with a number or $field, not " + test.operand()
                        : "a value can't hold $ < > = ! & or |, got " + test.operand());
                }
                index++;
                requireOk |= test == null;
                if (test != null) {
                    tests.add(test);
                }
            }
            if (tests.isEmpty() && !requireOk) {
                throw expected(PREDICATE, end);
            }
            return Condition.of(tests, requireOk);
        }

        private SyntaxError notAPredicate(int end) {
            String hint = likelyPredicate(end);
            return hint != null ? error(column(index), hint) : expected(PREDICATE, end);
        }

        /** The hint for a predicate starting at the cursor, written in one token or two; none at a stop word. */
        private String likelyPredicate(int end) {
            if (STOP_WORDS.contains(text(index))) {
                return null;
            }
            String hint = predicateHint(index, end);
            return hint == null && index + 1 < end ? predicateHint(index + 1, end) : hint;
        }

        /** {@code every 5t} where the construct takes {@code every=5t}. */
        private SyntaxError optionWrittenAsWord(int end) {
            String name = text(index);
            return error(column(index), name + " is an option here: " + name + "="
                + (index + 1 < end ? text(index + 1) : "…"));
        }

        /** The construct's own {@code name=value} options, to the end, by name. */
        private Map<String, Lexer.Token> options(int end, Map<String, CommandSpec.Option> allowed)
            throws SyntaxError {
            Map<String, Lexer.Token> options = new LinkedHashMap<>();
            for (; index < end; index++) {
                Lexer.Token token = tokens.get(index);
                int equals = Lexicon.optionEquals(token.text());
                if (equals < 0) {
                    throw allowed.containsKey(token.text()) ? optionWrittenAsWord(end)
                        : error(token.column(), "unexpected " + token.text());
                }
                String name = token.text().substring(0, equals);
                CommandSpec.Option option = allowed.get(name);
                if (option == null) {
                    throw error(token.column(), "unknown option " + name + "= (takes "
                        + String.join("=, ", allowed.keySet().stream().sorted().toList()) + "=)");
                }
                String value = token.text().substring(equals + 1);
                if (option.value().match(value) == null) {
                    throw error(token.column(), name + "= takes " + option.expectation() + ", got " + value);
                }
                if (options.put(name, token) != null) {
                    throw error(token.column(), name + "= given twice");
                }
            }
            return options;
        }

        private static String value(Lexer.Token option) {
            return option.text().substring(option.text().indexOf('=') + 1);
        }

        /** A duration option's value, which the option's types have checked; a plain number is seconds. */
        private static Duration duration(Lexer.Token option) {
            String value = value(option);
            return Lexicon.isDuration(value) ? Duration.parse(value) : Duration.seconds(Double.parseDouble(value));
        }

        /** {@code clock=} states where the query runs, so it must name the side the query's command runs on. */
        private void checkClock(Statement.Call query, Map<String, Lexer.Token> options) throws SyntaxError {
            Lexer.Token clock = options.get("clock");
            String side = query.entry().spec().runsOnServer() ? "server" : "client";
            if (clock != null && !value(clock).equals(side)) {
                throw error(clock.column(), "clock=" + value(clock) + " doesn't fit " + query.entry().qualifiedName()
                    + ", which runs on the " + side);
            }
        }

        /** The token at the cursor, which must be of the type; the cursor moves past it. */
        private String expectToken(ArgType type, int end) throws SyntaxError {
            if (index == end || !type.matches(text(index))) {
                throw expected(type.description(), end);
            }
            return text(index++);
        }

        private void expectWord(String word, int end) throws SyntaxError {
            if (index == end || !text(index).equals(word)) {
                throw expected(word, end);
            }
            index++;
        }

        private void expectEnd(int end) throws SyntaxError {
            if (index < end) {
                throw error(column(index), "unexpected " + text(index));
            }
        }

        private SyntaxError expected(String what, int end) {
            return index < end ? error(column(index), what + " expected, got " + text(index))
                : error(column(index), "missing " + what);
        }

        /** An error of the construct being read, followed by its usage. */
        private SyntaxError error(int column, String message) {
            return new SyntaxError(column, message + "; " + String.join(" | ", construct.usages()));
        }

        /** The token's column, or where a token after the step's last would start. */
        private int column(int position) {
            return position < tokens.size() ? tokens.get(position).column() : width + 2;
        }

        /** One attempt to match a form against the tokens from {@code from} to {@code end}. */
        private final class Matcher {
            private final CommandSpec spec;
            private final int end;
            private int position;
            private final Map<String, Args.Value> arguments = new LinkedHashMap<>();
            private final Map<String, Args.Value> options = new LinkedHashMap<>();
            private boolean full;
            /** What the optional parts skipped at {@link #skippedAt} would have taken, for an error there. */
            private final Set<String> skipped = new LinkedHashSet<>();
            private int skippedAt = -1;

            Matcher(CommandSpec spec, int from, int end) {
                this.spec = spec;
                this.position = from;
                this.end = end;
            }

            /** {@code textAfter}: free text may start right after these parts. */
            void elements(List<Form.Element> parts, boolean textAfter) throws Mismatch {
                for (int part = 0; part < parts.size(); part++) {
                    boolean textMayFollow = textMayStart(parts, part, textAfter);
                    switch (parts.get(part)) {
                        case Slot slot when slot.isText() -> freeText(slot);
                        case Slot slot -> slot(slot, textMayFollow);
                        case Form.Group group -> {
                            options(textMayFollow);
                            if (position < end && group.first().match(text(position)) != null) {
                                elements(group.elements(), textMayStart(parts, part + 1, textAfter));
                            } else {
                                skip(group.first());
                            }
                        }
                    }
                }
            }

            /** Whether free text may start at the part: only optional parts stand between them. */
            private static boolean textMayStart(List<Form.Element> parts, int from, boolean textAfter) {
                for (int part = from; part < parts.size(); part++) {
                    if (parts.get(part) instanceof Slot slot && (slot.isText() || !slot.optional())) {
                        return slot.isText();
                    }
                }
                return textAfter;
            }

            private void slot(Slot slot, boolean textMayFollow) throws Mismatch {
                options(textMayFollow);
                ArgType type = position < end ? slot.match(text(position)) : null;
                if (type != null) {
                    arguments.put(slot.name(), new Args.Value(text(position), type));
                    position++;
                } else if (slot.optional()) {
                    skip(slot);
                } else {
                    throw expectation(slot.expectation());
                }
            }

            private void skip(Slot slot) {
                if (position < end) {
                    if (skippedAt != position) {
                        skipped.clear();
                        skippedAt = position;
                    }
                    skipped.add(slot.expectation());
                }
            }

            private void freeText(Slot slot) throws Mismatch {
                options(true);
                if (position < end) {
                    Lexer.Token last = tokens.get(end - 1);
                    String text = source.substring(tokens.get(position).offset(), last.offset() + last.text().length());
                    arguments.put(slot.name(), new Args.Value(text, ArgType.TEXT));
                    position = end;
                } else if (!slot.optional()) {
                    throw expectation("<" + slot.name() + "> (text)");
                }
            }

            void finish() throws Mismatch {
                options(false);
                if (position < end) {
                    throw expectation(null);
                }
            }

            /** Takes the options (and {@code full}) at the cursor; where text may start, only declared ones. */
            private void options(boolean textMayFollow) throws Mismatch {
                while (position < end) {
                    String token = text(position);
                    if (spec.isDiffable() && token.equals("full")) {
                        if (full) {
                            throw new Mismatch(position, Set.of(), token, "full given twice");
                        }
                        full = true;
                        position++;
                        continue;
                    }
                    int equals = Lexicon.optionEquals(token);
                    if (equals < 0) {
                        return;
                    }
                    String name = token.substring(0, equals);
                    CommandSpec.Option option = spec.findOption(name);
                    if (option == null) {
                        if (textMayFollow) {
                            return;
                        }
                        List<String> names = optionNames();
                        throw new Mismatch(position, Set.of(), token, "unknown option " + name + "= ("
                            + (names.isEmpty() ? "takes none" : "takes " + String.join("=, ", names) + "=") + ")");
                    }
                    String value = token.substring(equals + 1);
                    ArgType type = option.value().isText() ? ArgType.TEXT : option.value().match(value);
                    if (type == null) {
                        throw new Mismatch(position, Set.of(), token, name + "= takes " + option.expectation()
                            + ", got " + value);
                    }
                    if (options.put(name, new Args.Value(value, type)) != null) {
                        throw new Mismatch(position, Set.of(), token, name + "= given twice");
                    }
                    position++;
                }
            }

            private List<String> optionNames() {
                List<String> names = new ArrayList<>();
                for (CommandSpec.Option option : spec.options().values()) {
                    names.add(option.usageName());
                }
                if (spec.kind() == CommandSpec.Kind.ACTION) {
                    names.add(CommandSpec.TIMEOUT.name());
                }
                return names;
            }

            /** A mismatch at the cursor: what was expected there, with what the optional parts skipped there take. */
            private Mismatch expectation(String expected) {
                Set<String> all = new LinkedHashSet<>(skippedAt == position ? skipped : Set.of());
                if (expected != null) {
                    all.add(expected);
                }
                return new Mismatch(position, all, position < end ? text(position) : null, null);
            }
        }
    }

    /**
     * Why a form didn't match, at which token: what could have stood there (merged across forms failing at the same
     * token), nothing (the token was unexpected), or a message of its own, which wins at its token.
     */
    private static final class Mismatch extends Exception {
        private final int position;
        private final Set<String> expected;
        private final String got;
        private final String own;
        private final List<String> usages = new ArrayList<>();

        Mismatch(int position, Set<String> expected, String got, String own) {
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

        /**
         * The one that got further; at the same token, expectations are merged. Every form takes an option-shaped
         * token at the same place and the same way, so the forms' own messages about a token are the same.
         */
        static Mismatch better(Mismatch best, Mismatch candidate) {
            if (best == null || candidate.position > best.position) {
                return candidate;
            }
            if (candidate.position < best.position) {
                return best;
            }
            Set<String> merged = new LinkedHashSet<>(best.expected);
            merged.addAll(candidate.expected);
            Mismatch combined = new Mismatch(best.position, merged, best.got, best.own);
            combined.usages.addAll(best.usages);
            combined.usages.addAll(candidate.usages);
            return combined;
        }

        String message() {
            String what;
            if (own != null) {
                what = own;
            } else if (expected.isEmpty()) {
                what = "unexpected " + got;
            } else {
                String options = String.join(" or ", expected);
                what = got == null ? "missing " + options : options + " expected, got " + got;
            }
            return what + "; " + String.join(" | ", usages);
        }
    }
}

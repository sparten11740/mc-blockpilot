package dev.blockpilot.api;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * The parsed arguments and {@code name=value} options of one command, with the type each one was taken as. The
 * typed accessors check that type, so asking for an integer when the slot took an id is a bug in the handler.
 */
public final class Args {
    /** A token as written and the type it was taken as; options declared without types are {@link ArgType#TEXT}. */
    public record Value(String text, ArgType type) {
        public Value {
            Objects.requireNonNull(text);
            Objects.requireNonNull(type);
        }
    }

    private final Map<String, Value> arguments;
    private final Map<String, Value> options;

    public Args(Map<String, Value> arguments, Map<String, Value> options) {
        this.arguments = Collections.unmodifiableMap(new LinkedHashMap<>(arguments));
        this.options = Collections.unmodifiableMap(new LinkedHashMap<>(options));
    }

    /** The type the argument was taken as, or null when it is absent. */
    public ArgType type(String name) {
        Value value = arguments.get(name);
        return value == null ? null : value.type();
    }

    public ArgType optionType(String name) {
        Value value = options.get(name);
        return value == null ? null : value.type();
    }

    public boolean has(String name) {
        return arguments.containsKey(name);
    }

    public boolean hasOption(String name) {
        return options.containsKey(name);
    }

    /** The argument as written, whatever its type; for a keyword, its word. */
    public String word(String name) {
        Value value = arguments.get(name);
        return value == null ? null : value.text();
    }

    public String text(String name) {
        return word(name);
    }

    public Position position(String name) {
        String token = typed(name, ArgType.POSITION);
        return token == null ? null : Position.parse(token);
    }

    public Point point(String name) {
        String token = typed(name, ArgType.POINT);
        return token == null ? null : Point.parse(token);
    }

    public EntityRef entity(String name) {
        String token = typed(name, ArgType.ENTITY);
        return token == null ? null : EntityRef.parse(token);
    }

    public UUID uuid(String name) {
        String token = typed(name, ArgType.UUID);
        return token == null ? null : UUID.fromString(token);
    }

    /** The index of a widget {@code w<i>} or a menu slot {@code s<i>}. */
    public int index(String name, int fallback) {
        String token = typed(name, ArgType.WIDGET, ArgType.MENU_SLOT);
        return token == null ? fallback : Integer.parseInt(token.substring(1));
    }

    public int integer(String name, int fallback) {
        String token = typed(name, ArgType.INT);
        return token == null ? fallback : Integer.parseInt(token);
    }

    public double number(String name, double fallback) {
        String token = typed(name, ArgType.NUMBER, ArgType.INT, ArgType.SECONDS);
        return token == null ? fallback : Double.parseDouble(token);
    }

    /** A {@code x<n>} count, or the fallback when absent. */
    public int count(String name, int fallback) {
        String token = typed(name, ArgType.COUNT);
        return token == null ? fallback : Integer.parseInt(token.substring(1));
    }

    /** A duration; a slot of type {@link ArgType#SECONDS} counts as seconds. */
    public Duration duration(String name) {
        return toDuration(typed(name, ArgType.DURATION, ArgType.SECONDS));
    }

    private String typed(String name, ArgType... accepted) {
        Value value = arguments.get(name);
        if (value == null) {
            return null;
        }
        if (!List.of(accepted).contains(value.type())) {
            throw new IllegalStateException(name + " was taken as " + value.type().production() + ", not as "
                + accepted[0].production());
        }
        return value.text();
    }

    /** The option's value as written. */
    public String option(String name) {
        Value value = options.get(name);
        return value == null ? null : value.text();
    }

    /** The options of a family ({@code rule:*}): name after the prefix → value, in the order given. */
    public Map<String, String> family(String prefix) {
        Map<String, String> family = new LinkedHashMap<>();
        options.forEach((name, value) -> {
            if (name.startsWith(prefix + ":")) {
                family.put(name.substring(prefix.length() + 1), value.text());
            }
        });
        return family;
    }

    public double numberOption(String name, double fallback) throws CommandException {
        String value = typedOption(name, Lexicon::isDecimal, "a number", ArgType.NUMBER, ArgType.INT,
            ArgType.SECONDS);
        return value == null ? fallback : Double.parseDouble(value);
    }

    public int intOption(String name, int fallback) throws CommandException {
        String value = typedOption(name, Lexicon::isInteger, "a whole number", ArgType.INT);
        return value == null ? fallback : Integer.parseInt(value);
    }

    /** A duration option; a plain number counts as seconds, e.g. {@code timeout=30}. */
    public Duration durationOption(String name) throws CommandException {
        return toDuration(typedOption(name, value -> Lexicon.isDuration(value) || Lexicon.isSeconds(value),
            "a duration like 20t or 3s", ArgType.DURATION, ArgType.SECONDS));
    }

    /** An option declared without types is checked here, when the handler reads it. */
    private String typedOption(String name, Predicate<String> untyped, String expected, ArgType... accepted)
        throws CommandException {
        Value value = options.get(name);
        if (value == null) {
            return null;
        }
        if (value.type() == ArgType.TEXT) {
            if (!untyped.test(value.text())) {
                throw new CommandException("syntax", name + "= expects " + expected + ", got " + value.text());
            }
            return value.text();
        }
        if (!List.of(accepted).contains(value.type())) {
            throw new IllegalStateException(name + "= was taken as " + value.type().production() + ", not as "
                + accepted[0].production());
        }
        return value.text();
    }

    private static Duration toDuration(String token) {
        if (token == null) {
            return null;
        }
        return Lexicon.isDuration(token) ? Duration.parse(token) : Duration.seconds(Double.parseDouble(token));
    }

    public Map<String, Value> arguments() {
        return arguments;
    }

    public Map<String, Value> options() {
        return options;
    }
}

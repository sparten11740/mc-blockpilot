package dev.blockpilot.api;

import java.util.LinkedHashMap;
import java.util.Map;

/** The parsed arguments and {@code name=value} options of one command, with the type each argument matched. */
public final class Args {
    private final Map<String, String> values;
    private final Map<String, ArgType> kinds;
    private final Map<String, String> options;

    public Args(Map<String, String> values, Map<String, ArgType> kinds, Map<String, String> options) {
        this.values = values;
        this.kinds = kinds;
        this.options = options;
    }

    /** The type the argument was matched as, or null when it is absent. */
    public ArgType kind(String name) {
        return kinds.get(name);
    }

    public boolean has(String name) {
        return values.containsKey(name);
    }

    public boolean hasOption(String name) {
        return options.containsKey(name);
    }

    public String word(String name) {
        return values.get(name);
    }

    public String text(String name) {
        return values.get(name);
    }

    public Position position(String name) {
        String token = values.get(name);
        return token == null ? null : Position.parse(token);
    }

    public EntityRef entity(String name) {
        String token = values.get(name);
        return token == null ? null : EntityRef.parse(token);
    }

    public int integer(String name, int fallback) {
        String token = values.get(name);
        return token == null ? fallback : Integer.parseInt(token);
    }

    public double number(String name, double fallback) {
        String token = values.get(name);
        return token == null ? fallback : Double.parseDouble(token);
    }

    /** A {@code x<n>} count, or the fallback when absent. */
    public int count(String name, int fallback) {
        String token = values.get(name);
        return token == null ? fallback : Integer.parseInt(token.substring(1));
    }

    public Duration duration(String name) {
        String token = values.get(name);
        return token == null ? null : Duration.parse(token);
    }

    public String option(String name) {
        return options.get(name);
    }

    /** The options of a family ({@code rule:*}): name after the prefix → value, in the order given. */
    public Map<String, String> family(String prefix) {
        Map<String, String> family = new LinkedHashMap<>();
        options.forEach((name, value) -> {
            if (name.startsWith(prefix + ":")) {
                family.put(name.substring(prefix.length() + 1), value);
            }
        });
        return family;
    }

    public double numberOption(String name, double fallback) throws CommandException {
        String value = options.get(name);
        try {
            return value == null ? fallback : Double.parseDouble(value);
        } catch (NumberFormatException e) {
            throw new CommandException("syntax", name + "= expects a number, got " + value);
        }
    }

    public int intOption(String name, int fallback) throws CommandException {
        String value = options.get(name);
        try {
            return value == null ? fallback : Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new CommandException("syntax", name + "= expects a whole number, got " + value);
        }
    }

    /** A duration option; a bare number counts as seconds, e.g. {@code timeout=30}. */
    public Duration durationOption(String name) throws CommandException {
        String value = options.get(name);
        if (value == null) {
            return null;
        }
        Duration duration = Duration.parse(value);
        if (duration != null) {
            return duration;
        }
        try {
            return Duration.seconds(Double.parseDouble(value));
        } catch (NumberFormatException e) {
            throw new CommandException("syntax", name + "= expects a duration like 20t or 3s, got " + value);
        }
    }

    public Map<String, String> values() {
        return values;
    }

    public Map<String, String> options() {
        return options;
    }
}

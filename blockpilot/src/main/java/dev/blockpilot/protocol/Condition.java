package dev.blockpilot.protocol;

import dev.blockpilot.api.Fields;
import dev.blockpilot.api.Lexicon;
import dev.blockpilot.api.Position;
import dev.blockpilot.api.Result;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The predicates of {@code wait <query> until …}, {@code assert} and {@code if}: {@code <field><op><value>} joined by
 * {@code and}, or {@code ok}. Numbers compare numerically, anything else by text; {@code count} is the number of
 * rows; a value written {@code $<field>} is that field of the same result.
 */
public final class Condition {
    private final List<Lexicon.Test> tests;
    private final boolean requireOk;

    private Condition(List<Lexicon.Test> tests, boolean requireOk) {
        this.tests = List.copyOf(tests);
        this.requireOk = requireOk;
    }

    /** At least one test, or {@code requireOk}. */
    static Condition of(List<Lexicon.Test> tests, boolean requireOk) {
        if (tests.isEmpty() && !requireOk) {
            throw new IllegalArgumentException("a condition needs a test or ok");
        }
        return new Condition(tests, requireOk);
    }

    public boolean holds(Result result) {
        if (!result.isOk()) {
            return false;
        }
        for (Lexicon.Test test : tests) {
            Object actual = lookup(result, test.field());
            String expected = test.operand();
            if (test.reference() != null) {
                Object other = lookup(result, test.reference());
                expected = other == null ? null : String.valueOf(other);
            }
            if (actual == null || expected == null || !compare(actual, test.comparison(), expected)) {
                return false;
            }
        }
        return true;
    }

    /** The fields the predicates name, including fields compared against, as last observed; attached to the result. */
    public Fields observed(Result result) {
        Fields fields = new Fields();
        List<String> names = new ArrayList<>();
        for (Lexicon.Test test : tests) {
            names.add(test.field());
            if (test.reference() != null) {
                names.add(test.reference());
            }
        }
        for (String name : names.stream().distinct().toList()) {
            Object value = result == null ? null : lookup(result, name);
            fields.put(name, value == null ? "none" : value);
        }
        return fields;
    }

    static Object lookup(Result result, String path) {
        if (path.equals("count") && result.fields().get("count") == null) {
            return result.rows() == null ? null : result.rows().size();
        }
        String[] parts = path.split("\\.");
        Object value = result.fields().get(parts[0]);
        for (int index = 1; index < parts.length && value != null; index++) {
            value = child(value, parts[index]);
        }
        return value;
    }

    private static Object child(Object value, String part) {
        if (value instanceof Position position) {
            return switch (part) {
                case "x" -> position.x();
                case "y" -> position.y();
                case "z" -> position.z();
                default -> null;
            };
        }
        if (value instanceof Map<?, ?> map) {
            return map.get(part);
        }
        // Field paths are checked by the parser: a part starting with a digit is a list index that fits an int.
        if (value instanceof List<?> list && Character.isDigit(part.charAt(0))) {
            int index = Integer.parseInt(part);
            return index < list.size() ? list.get(index) : null;
        }
        return null;
    }

    static boolean compare(Object actual, String operator, String expected) {
        Double left = number(actual);
        Double right = number(expected);
        if (left != null && right != null) {
            int order = Double.compare(left, right);
            return switch (operator) {
                case "==" -> order == 0;
                case "!=" -> order != 0;
                case "<" -> order < 0;
                case "<=" -> order <= 0;
                case ">" -> order > 0;
                default -> order >= 0;
            };
        }
        String text = withoutDefaultNamespace(String.valueOf(actual));
        String wanted = withoutDefaultNamespace(expected);
        return switch (operator) {
            case "==" -> text.equals(wanted);
            case "!=" -> !text.equals(wanted);
            default -> false;
        };
    }

    /** Ids are printed without {@code minecraft:} and may be written with it. */
    private static String withoutDefaultNamespace(String value) {
        return value.startsWith("minecraft:") ? value.substring("minecraft:".length()) : value;
    }

    private static Double number(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof Boolean) {
            return null;
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}

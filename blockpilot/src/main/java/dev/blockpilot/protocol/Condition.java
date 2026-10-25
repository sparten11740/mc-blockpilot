package dev.blockpilot.protocol;

import dev.blockpilot.api.CommandException;
import dev.blockpilot.api.Fields;
import dev.blockpilot.api.Position;
import dev.blockpilot.api.Result;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The predicates of {@code wait <query> until …}: {@code <field><op><value>} joined by {@code and}, or {@code ok}.
 * Numbers compare numerically, anything else by text; {@code count} is the number of rows; a value written
 * {@code $<field>} is that field of the same result.
 */
public final class Condition {
    private static final Pattern PREDICATE = Pattern.compile("([A-Za-z_][\\w.]*)(==|!=|<=|>=|<|>)(.+)");

    private record Predicate(String field, String operator, String value) {
    }

    private final List<Predicate> predicates;
    private final boolean requireOk;

    private Condition(List<Predicate> predicates, boolean requireOk) {
        this.predicates = predicates;
        this.requireOk = requireOk;
    }

    public static Condition parse(List<String> tokens) throws CommandException {
        List<Predicate> predicates = new ArrayList<>();
        boolean requireOk = false;
        for (String token : tokens) {
            if (token.equals("and")) {
                continue;
            }
            if (token.equals("ok")) {
                requireOk = true;
                continue;
            }
            Matcher matcher = PREDICATE.matcher(token);
            if (!matcher.matches()) {
                throw new CommandException("syntax", "predicate expected (field==value, field<n, ok), got " + token);
            }
            predicates.add(new Predicate(matcher.group(1), matcher.group(2), matcher.group(3)));
        }
        if (predicates.isEmpty() && !requireOk) {
            throw new CommandException("syntax", "wait … until needs at least one predicate");
        }
        return new Condition(predicates, requireOk);
    }

    public boolean holds(Result result) {
        if (!result.isOk()) {
            return false;
        }
        for (Predicate predicate : predicates) {
            Object actual = lookup(result, predicate.field());
            String expected = predicate.value();
            if (reference(expected) != null) {
                Object other = lookup(result, reference(expected));
                expected = other == null ? null : String.valueOf(other);
            }
            if (actual == null || expected == null || !compare(actual, predicate.operator(), expected)) {
                return false;
            }
        }
        return true;
    }

    /** The field a {@code $<field>} value refers to, or null for a literal. */
    private static String reference(String value) {
        return value.length() > 1 && value.charAt(0) == '$' ? value.substring(1) : null;
    }

    /** The fields the predicates name, including fields compared against, as last observed; attached to the result. */
    public Fields observed(Result result) {
        Fields fields = new Fields();
        List<String> names = new ArrayList<>();
        for (Predicate predicate : predicates) {
            names.add(predicate.field());
            if (reference(predicate.value()) != null) {
                names.add(reference(predicate.value()));
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
        if (value instanceof List<?> list && part.matches("\\d+")) {
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
        String text = String.valueOf(actual);
        return switch (operator) {
            case "==" -> text.equals(expected) || text.equals("minecraft:" + expected);
            case "!=" -> !text.equals(expected) && !text.equals("minecraft:" + expected);
            default -> false;
        };
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

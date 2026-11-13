package dev.blockpilot.game;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** Mod config values as agents write them, read by the type of the key's default value. */
final class ConfigValues {
    private ConfigValues() {
    }

    /**
     * The value the text stands for, as the default's type, or null when it is none. A list's items are read as the
     * default's items are, or the current value's when the default is empty, else as text.
     */
    static Object parse(String text, Object defaultValue, Object current) {
        if (defaultValue instanceof List<?> list) {
            Object sample = sample(list, current);
            List<Object> items = new ArrayList<>();
            if (!text.isBlank()) {
                for (String item : text.split(",", -1)) {
                    Object value = sample == null ? item.strip() : parse(item.strip(), sample, null);
                    if (value == null) {
                        return null;
                    }
                    items.add(value);
                }
            }
            return items;
        }
        if (defaultValue instanceof Boolean) {
            return switch (text) {
                case "true", "on" -> true;
                case "false", "off" -> false;
                default -> null;
            };
        }
        if (defaultValue instanceof Integer) {
            return number(text, Integer::valueOf);
        }
        if (defaultValue instanceof Long) {
            return number(text, Long::valueOf);
        }
        if (defaultValue instanceof Double) {
            return finite(number(text, Double::valueOf));
        }
        if (defaultValue instanceof Float) {
            return finite(number(text, Float::valueOf));
        }
        if (defaultValue instanceof Enum<?> constant) {
            return Arrays.stream(constant.getDeclaringClass().getEnumConstants())
                .filter(each -> each.name().equalsIgnoreCase(text)).findFirst().orElse(null);
        }
        return defaultValue instanceof String ? text : null;
    }

    /** The value as the file writes it: enum names, lists separated by commas. */
    static String text(Object value) {
        if (value == null) {
            return "none";
        }
        if (value instanceof Enum<?> constant) {
            return constant.name();
        }
        if (value instanceof List<?> list) {
            return list.stream().map(ConfigValues::text).collect(Collectors.joining(","));
        }
        return String.valueOf(value);
    }

    /** What the key takes: {@code true|false}, a range like {@code 0..64}, the names the mod accepts, a kind. */
    static String takes(Object defaultValue, Object current, Object min, Object max, Predicate<Object> accepts) {
        if (defaultValue instanceof List<?> list) {
            Object sample = sample(list, current);
            return "items separated by commas, each " + (sample == null ? "text" : takes(sample, null, null, null,
                each -> true));
        }
        if (defaultValue instanceof Boolean) {
            return "true|false";
        }
        if (defaultValue instanceof Number) {
            if (min != null && max != null) {
                return text(min) + ".." + text(max);
            }
            return defaultValue instanceof Integer || defaultValue instanceof Long ? "a whole number" : "a number";
        }
        if (defaultValue instanceof Enum<?> constant) {
            return Arrays.stream(constant.getDeclaringClass().getEnumConstants())
                .filter(accepts).map(Enum::name).collect(Collectors.joining("|"));
        }
        if (defaultValue instanceof String) {
            return "text";
        }
        return "a " + defaultValue.getClass().getSimpleName() + ", which can't be written as text";
    }

    private static Object sample(List<?> list, Object current) {
        if (!list.isEmpty()) {
            return list.get(0);
        }
        return current instanceof List<?> items && !items.isEmpty() ? items.get(0) : null;
    }

    private static Object number(String text, Function<String, Object> parse) {
        try {
            return parse.apply(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Java reads NaN and Infinity as numbers; a config never means them. */
    private static Object finite(Object value) {
        return value instanceof Number number && Double.isFinite(number.doubleValue()) ? value : null;
    }
}

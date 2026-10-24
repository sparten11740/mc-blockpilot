package dev.blockpilot.api;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

/** The compact text form of a field value. */
public final class Values {
    private Values() {
    }

    public static String text(Object value) {
        if (value instanceof Double || value instanceof Float) {
            return Numbers.format(((Number) value).doubleValue());
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(Values::text).collect(Collectors.joining(","));
        }
        if (value instanceof Map<?, ?> map) {
            return map.entrySet().stream()
                .map(entry -> entry.getKey() + ":" + text(entry.getValue()))
                .collect(Collectors.joining(",", "{", "}"));
        }
        return String.valueOf(value);
    }
}

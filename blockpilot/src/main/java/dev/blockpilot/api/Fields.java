package dev.blockpilot.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * An ordered set of named values, each with its compact text form. Results, rows and events are built from these,
 * so the text and JSON renderings always carry the same data: JSON uses the name and value, text uses the text form.
 */
public final class Fields {
    /** One value. A null text means the default {@code name=value}; an empty text hides it from text output. */
    public record Field(String name, Object value, String text) {
    }

    private final List<Field> fields = new ArrayList<>();

    public List<Field> list() {
        return Collections.unmodifiableList(fields);
    }

    public boolean isEmpty() {
        return fields.isEmpty();
    }

    public Object get(String name) {
        for (Field field : fields) {
            if (field.name().equals(name)) {
                return field.value();
            }
        }
        return null;
    }

    /** Printed as {@code name=value}. */
    public Fields put(String name, Object value) {
        return add(name, value, null);
    }

    /** Printed as {@code <name><value>}, e.g. {@code hp20} or {@code t6000}. */
    public Fields tag(String name, Object value) {
        return add(name, value, name + Values.text(value));
    }

    /** Printed as the value alone, e.g. {@code overworld}. */
    public Fields bare(String name, Object value) {
        return add(name, value, Values.text(value));
    }

    /** Printed with an explicit text form. */
    public Fields put(String name, Object value, String text) {
        return add(name, value, text);
    }

    /** Only in JSON output, e.g. a field another field's text form already shows. */
    public Fields hidden(String name, Object value) {
        return add(name, value, "");
    }

    private Fields add(String name, Object value, String text) {
        if (value != null) {
            fields.add(new Field(name, value, text));
        }
        return this;
    }

    /** The text form: every visible field, space-separated. */
    public String text() {
        StringBuilder out = new StringBuilder();
        for (Field field : fields) {
            String text = field.text() != null ? field.text() : field.name() + "=" + Values.text(field.value());
            if (text.isEmpty()) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(text);
        }
        return out.toString();
    }

    /** The values as a name → value map, in order. */
    public Map<String, Object> asMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        for (Field field : fields) {
            map.put(field.name(), field.value());
        }
        return map;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Fields that && asMap().equals(that.asMap()) && text().equals(that.text());
    }

    @Override
    public int hashCode() {
        return asMap().hashCode();
    }
}

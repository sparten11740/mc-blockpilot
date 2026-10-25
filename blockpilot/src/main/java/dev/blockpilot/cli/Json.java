package dev.blockpilot.cli;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON for the client, which runs outside the game and so without the game's Gson. Objects read as ordered maps,
 * arrays as lists and numbers as {@link BigDecimal}, so a value written back keeps its digits.
 */
public final class Json {
    private final String text;
    private int position;

    private Json(String text) {
        this.text = text;
    }

    public static Object parse(String text) {
        Json reader = new Json(text);
        reader.skipWhitespace();
        Object value = reader.value();
        reader.skipWhitespace();
        if (reader.position < text.length()) {
            throw reader.error("unexpected text after the value");
        }
        return value;
    }

    public static String write(Object value) {
        StringBuilder out = new StringBuilder();
        write(value, out);
        return out.toString();
    }

    private Object value() {
        if (position >= text.length()) {
            throw error("unexpected end");
        }
        return switch (text.charAt(position)) {
            case '{' -> object();
            case '[' -> array();
            case '"' -> string();
            case 't' -> literal("true", Boolean.TRUE);
            case 'f' -> literal("false", Boolean.FALSE);
            case 'n' -> literal("null", null);
            default -> number();
        };
    }

    private Map<String, Object> object() {
        position++;
        Map<String, Object> object = new LinkedHashMap<>();
        skipWhitespace();
        if (accept('}')) {
            return object;
        }
        do {
            skipWhitespace();
            if (position >= text.length() || text.charAt(position) != '"') {
                throw error("expected a key");
            }
            String key = string();
            skipWhitespace();
            expect(':');
            skipWhitespace();
            object.put(key, value());
            skipWhitespace();
        } while (accept(','));
        expect('}');
        return object;
    }

    private List<Object> array() {
        position++;
        List<Object> array = new ArrayList<>();
        skipWhitespace();
        if (accept(']')) {
            return array;
        }
        do {
            skipWhitespace();
            array.add(value());
            skipWhitespace();
        } while (accept(','));
        expect(']');
        return array;
    }

    private String string() {
        position++;
        StringBuilder out = new StringBuilder();
        while (true) {
            if (position >= text.length()) {
                throw error("unterminated string");
            }
            char next = text.charAt(position++);
            if (next == '"') {
                return out.toString();
            }
            if (next < 0x20) {
                throw error("control character in a string");
            }
            if (next != '\\') {
                out.append(next);
                continue;
            }
            if (position >= text.length()) {
                throw error("unterminated string");
            }
            char escape = text.charAt(position++);
            switch (escape) {
                case '"', '\\', '/' -> out.append(escape);
                case 'b' -> out.append('\b');
                case 'f' -> out.append('\f');
                case 'n' -> out.append('\n');
                case 'r' -> out.append('\r');
                case 't' -> out.append('\t');
                case 'u' -> out.append(unicodeEscape());
                default -> throw error("bad escape \\" + escape);
            }
        }
    }

    private char unicodeEscape() {
        int code = 0;
        for (int i = 0; i < 4; i++) {
            int digit = position < text.length() ? Character.digit(text.charAt(position), 16) : -1;
            if (digit < 0) {
                throw error("bad \\u escape");
            }
            code = code * 16 + digit;
            position++;
        }
        return (char) code;
    }

    private BigDecimal number() {
        int start = position;
        accept('-');
        if (!accept('0') && !digits()) {
            throw error("expected a value");
        }
        if (accept('.') && !digits()) {
            throw error("expected digits after the point");
        }
        if (accept('e') || accept('E')) {
            if (!accept('+')) {
                accept('-');
            }
            if (!digits()) {
                throw error("expected digits in the exponent");
            }
        }
        return new BigDecimal(text.substring(start, position));
    }

    private Object literal(String word, Object value) {
        if (!text.startsWith(word, position)) {
            throw error("expected a value");
        }
        position += word.length();
        return value;
    }

    private boolean digits() {
        int start = position;
        while (position < text.length() && text.charAt(position) >= '0' && text.charAt(position) <= '9') {
            position++;
        }
        return position > start;
    }

    private boolean accept(char expected) {
        if (position < text.length() && text.charAt(position) == expected) {
            position++;
            return true;
        }
        return false;
    }

    private void expect(char expected) {
        if (!accept(expected)) {
            throw error("expected " + expected);
        }
    }

    private void skipWhitespace() {
        while (position < text.length() && " \t\r\n".indexOf(text.charAt(position)) >= 0) {
            position++;
        }
    }

    private IllegalArgumentException error(String problem) {
        return new IllegalArgumentException("bad JSON at " + position + ": " + problem);
    }

    private static void write(Object value, StringBuilder out) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof String string) {
            quote(string, out);
        } else if (value instanceof Boolean) {
            out.append(value);
        } else if (value instanceof Number number) {
            String digits = number.toString();
            if (digits.equals("NaN") || digits.endsWith("Infinity")) {
                throw new IllegalArgumentException("JSON has no " + digits);
            }
            out.append(digits);
        } else if (value instanceof Map<?, ?> object) {
            out.append('{');
            String separator = "";
            for (Map.Entry<?, ?> entry : object.entrySet()) {
                out.append(separator);
                quote(entry.getKey().toString(), out);
                out.append(':');
                write(entry.getValue(), out);
                separator = ",";
            }
            out.append('}');
        } else if (value instanceof List<?> array) {
            out.append('[');
            String separator = "";
            for (Object element : array) {
                out.append(separator);
                write(element, out);
                separator = ",";
            }
            out.append(']');
        } else {
            throw new IllegalArgumentException("not a JSON value: " + value.getClass().getName());
        }
    }

    private static void quote(String string, StringBuilder out) {
        out.append('"');
        for (int i = 0; i < string.length(); i++) {
            char next = string.charAt(i);
            switch (next) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                default -> {
                    if (next < 0x20) {
                        out.append(String.format("\\u%04x", (int) next));
                    } else {
                        out.append(next);
                    }
                }
            }
        }
        out.append('"');
    }
}

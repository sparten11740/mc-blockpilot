package dev.blockpilot.api;

import java.util.List;

/**
 * One argument of a form: a keyword (type {@link ArgType#CHOICE} with its words), free text (only last), or a token of
 * one or more types, the first matching one taken. The parsed value is stored under {@code name}.
 */
public record Slot(String name, List<ArgType> types, List<String> words, boolean optional) implements Form.Element {
    public Slot {
        types = List.copyOf(types);
        words = List.copyOf(words);
    }

    public boolean isText() {
        return types.contains(ArgType.TEXT);
    }

    public boolean isKeyword() {
        return types.contains(ArgType.CHOICE);
    }

    /** The type the token is taken as, or null when it doesn't fit. Text isn't matched by token. */
    public ArgType match(String token) {
        for (ArgType type : types) {
            if (type == ArgType.CHOICE ? words.contains(token) : type.matches(token)) {
                return type;
            }
        }
        return null;
    }

    /** As help shows it: {@code <pos>}, {@code [radius]} or {@code down|up}. */
    public String usage() {
        if (isKeyword()) {
            String alternatives = String.join("|", words);
            return optional ? "[" + alternatives + "]" : alternatives;
        }
        return optional ? "[" + name + "]" : "<" + name + ">";
    }

    /** What the slot takes, for error messages: {@code <dy> (a number)}. */
    public String expectation() {
        if (isKeyword()) {
            return String.join("|", words);
        }
        StringBuilder out = new StringBuilder("<").append(name).append("> (");
        for (int index = 0; index < types.size(); index++) {
            out.append(index == 0 ? "" : " or ").append(types.get(index).description());
        }
        return out.append(')').toString();
    }
}

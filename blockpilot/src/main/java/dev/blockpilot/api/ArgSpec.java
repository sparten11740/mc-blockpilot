package dev.blockpilot.api;

import java.util.List;

/** One positional argument of a command. */
public record ArgSpec(String name, ArgType type, boolean optional, List<String> choices) {
    /** The argument as help shows it, e.g. {@code <pos>}, {@code [radius]} or {@code <on|off>}. */
    public String usage() {
        String body = type == ArgType.CHOICE ? String.join("|", choices) : name;
        return optional ? "[" + body + "]" : "<" + body + ">";
    }

    /** Whether the token can be this argument; optional arguments are skipped when it cannot. */
    public boolean accepts(String token) {
        return switch (type) {
            case POSITION -> Position.parse(token) != null;
            case ENTITY -> EntityRef.parse(token) != null;
            case TARGET -> Position.parse(token) != null || EntityRef.parse(token) != null;
            case INT -> token.matches("-?\\d+");
            case NUMBER -> token.matches("-?\\d+(\\.\\d+)?");
            case COUNT -> token.matches("x\\d+");
            case DURATION -> Duration.parse(token) != null;
            case CHOICE -> choices.contains(token);
            case WORD, TEXT -> true;
        };
    }
}

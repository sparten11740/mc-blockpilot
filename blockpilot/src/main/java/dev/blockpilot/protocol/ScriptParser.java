package dev.blockpilot.protocol;

import java.util.ArrayList;
import java.util.List;

/** Splits a script into commands at {@code ;} and newlines; {@code \;} is a literal semicolon. */
public final class ScriptParser {
    private ScriptParser() {
    }

    public static List<RawCommand> parse(String script) {
        List<RawCommand> commands = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int index = 0; index < script.length(); index++) {
            char character = script.charAt(index);
            if (character == '\\' && index + 1 < script.length() && script.charAt(index + 1) == ';') {
                current.append(';');
                index++;
            } else if (character == ';' || character == '\n' || character == '\r') {
                add(commands, current);
            } else {
                current.append(character);
            }
        }
        add(commands, current);
        return commands;
    }

    private static void add(List<RawCommand> commands, StringBuilder current) {
        if (!current.toString().isBlank()) {
            commands.add(RawCommand.of(current.toString()));
        }
        current.setLength(0);
    }
}

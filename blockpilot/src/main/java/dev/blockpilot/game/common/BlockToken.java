package dev.blockpilot.game.common;

import dev.blockpilot.api.CommandException;
import dev.blockpilot.api.Lexicon;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** A block token split at its brackets: {@code oak_log[axis=y]} is the block {@code oak_log} with axis y. */
record BlockToken(String block, Map<String, String> states) {
    static BlockToken of(String token) throws CommandException {
        int open = token.indexOf('[');
        if (open < 0) {
            return new BlockToken(token, Map.of());
        }
        if (!Lexicon.isBlockState(token)) {
            throw new CommandException("syntax", "states are written [name=value,...], not " + token.substring(open));
        }
        Map<String, String> states = new LinkedHashMap<>();
        for (String state : token.substring(open + 1, token.length() - 1).split(",")) {
            int equals = state.indexOf('=');
            String name = state.substring(0, equals);
            if (states.put(name, state.substring(equals + 1)) != null) {
                throw new CommandException("syntax", "the state " + name + " is given twice in " + token);
            }
        }
        return new BlockToken(token.substring(0, open), Collections.unmodifiableMap(states));
    }
}

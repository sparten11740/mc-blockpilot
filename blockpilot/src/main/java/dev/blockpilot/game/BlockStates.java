package dev.blockpilot.game;

import dev.blockpilot.api.CommandException;
import dev.blockpilot.api.Lexicon;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** A block token split at its brackets: {@code oak_log[axis=y]} is the block {@code oak_log} with axis y. */
record BlockStates(String block, Map<String, String> states) {
    static BlockStates of(String token) throws CommandException {
        int open = token.indexOf('[');
        if (open < 0) {
            return new BlockStates(token, Map.of());
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
        return new BlockStates(token.substring(0, open), Collections.unmodifiableMap(states));
    }
}

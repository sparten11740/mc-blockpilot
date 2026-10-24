package dev.blockpilot.protocol;

import dev.blockpilot.api.ArgSpec;
import dev.blockpilot.api.ArgType;
import dev.blockpilot.api.Args;
import dev.blockpilot.api.CommandException;
import dev.blockpilot.api.CommandSpec;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Matches a command's tokens against its spec: positional arguments in order, {@code name=value} options anywhere. */
public final class ArgParser {
    /** Options every action accepts. */
    private static final List<String> ACTION_OPTIONS = List.of("timeout");

    private ArgParser() {
    }

    public static Args parse(CommandSpec spec, RawCommand command) throws CommandException {
        Map<String, String> values = new LinkedHashMap<>();
        Map<String, String> options = new LinkedHashMap<>();
        List<RawCommand.Token> tokens = command.tokens();
        int index = 0;
        for (ArgSpec arg : spec.args()) {
            index = skipOptions(spec, tokens, index, options);
            if (arg.type() == ArgType.TEXT) {
                String text = command.restFrom(index);
                if (text.isEmpty() && !arg.optional()) {
                    throw new CommandException("syntax", "missing " + arg.usage() + " in " + spec.usage(spec.name()));
                }
                if (!text.isEmpty()) {
                    values.put(arg.name(), text);
                }
                index = tokens.size();
                break;
            }
            if (index >= tokens.size()) {
                if (arg.optional()) {
                    continue;
                }
                throw new CommandException("syntax", "missing " + arg.usage() + " in " + spec.usage(spec.name()));
            }
            String token = tokens.get(index).text();
            if (arg.accepts(token)) {
                values.put(arg.name(), token);
                index++;
            } else if (!arg.optional()) {
                throw new CommandException("syntax", arg.usage() + " expected, got " + token);
            }
        }
        index = skipOptions(spec, tokens, index, options);
        if (index < tokens.size()) {
            throw new CommandException("syntax", "unexpected " + tokens.get(index).text() + " in "
                + spec.usage(spec.name()));
        }
        return new Args(values, options);
    }

    private static int skipOptions(CommandSpec spec, List<RawCommand.Token> tokens, int index,
        Map<String, String> options) {
        while (index < tokens.size()) {
            String token = tokens.get(index).text();
            int equals = token.indexOf('=');
            if (equals <= 0 || token.startsWith("==", equals)) {
                break;
            }
            String name = token.substring(0, equals);
            boolean known = spec.options().containsKey(name)
                || spec.kind() == CommandSpec.Kind.ACTION && ACTION_OPTIONS.contains(name);
            if (!known) {
                break;
            }
            options.put(name, token.substring(equals + 1));
            index++;
        }
        return index;
    }
}

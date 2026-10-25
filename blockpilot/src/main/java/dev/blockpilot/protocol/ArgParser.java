package dev.blockpilot.protocol;

import dev.blockpilot.api.ArgType;
import dev.blockpilot.api.Args;
import dev.blockpilot.api.CommandException;
import dev.blockpilot.api.CommandSpec;
import dev.blockpilot.api.Form;
import dev.blockpilot.api.Slot;
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
        Map<String, ArgType> kinds = new LinkedHashMap<>();
        Map<String, String> options = new LinkedHashMap<>();
        List<RawCommand.Token> tokens = command.tokens();
        String usage = spec.usages(spec.name()).get(0);
        int index = 0;
        for (Form.Element element : spec.forms().get(0).elements()) {
            Slot arg = (Slot) element;
            index = skipOptions(spec, tokens, index, options);
            if (arg.isText()) {
                String text = command.restFrom(index);
                if (text.isEmpty() && !arg.optional()) {
                    throw new CommandException("syntax", "missing " + arg.usage() + " in " + usage);
                }
                if (!text.isEmpty()) {
                    values.put(arg.name(), text);
                    kinds.put(arg.name(), ArgType.TEXT);
                }
                index = tokens.size();
                break;
            }
            if (index >= tokens.size()) {
                if (arg.optional()) {
                    continue;
                }
                throw new CommandException("syntax", "missing " + arg.usage() + " in " + usage);
            }
            String token = tokens.get(index).text();
            ArgType kind = arg.match(token);
            if (kind != null) {
                values.put(arg.name(), token);
                kinds.put(arg.name(), kind);
                index++;
            } else if (!arg.optional()) {
                throw new CommandException("syntax", arg.expectation() + " expected, got " + token);
            }
        }
        index = skipOptions(spec, tokens, index, options);
        if (index < tokens.size()) {
            throw new CommandException("syntax", "unexpected " + tokens.get(index).text() + " in "
                + usage);
        }
        return new Args(values, kinds, options);
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

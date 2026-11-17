package dev.blockpilot.protocol;

import dev.blockpilot.api.ArgType;
import dev.blockpilot.api.Args;
import dev.blockpilot.api.CommandException;
import dev.blockpilot.api.Lexicon;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * Turns the names in a command's arguments into what its handler sees, just before it runs: {@code @<name>} into the
 * entity's {@code e<id>}, and {@code sub:<uuid|name|here>} into the sub-level's {@code sub:<uuid>}. Free text keeps
 * them as written: only values taken as an entity or a sub-level are looked up.
 */
public final class References {
    private static final String SUB = "sub:";

    /** How the game finds what a name stands for. */
    public interface Lookup {
        /**
         * The id of the loaded entity tagged or called {@code name}, the one nearest where the server takes the player
         * to be when several are, or empty.
         */
        OptionalInt resolveEntity(String name) throws CommandException;

        /**
         * The sub-level a reference names by its UUID, its name or {@code here}; fails with {@code no_sublevel} or
         * {@code ambiguous}, and with {@code unsupported} without Sable.
         */
        UUID resolveSubLevel(String reference) throws CommandException;
    }

    private References() {
    }

    /** The arguments with every name looked up; the same arguments when they hold none. */
    public static Args resolve(Args args, Lookup lookup) throws CommandException {
        if (!holdsReferences(args)) {
            return args;
        }
        Map<String, String> resolved = new HashMap<>();
        Map<String, Args.Value> arguments = new LinkedHashMap<>();
        Map<String, List<Args.Value>> values = new LinkedHashMap<>();
        for (String name : args.arguments().keySet()) {
            List<Args.Value> each = new ArrayList<>();
            for (Args.Value value : args.values(name)) {
                each.add(resolve(value, lookup, resolved));
            }
            // Every argument has a value, and the accessors read its first.
            arguments.put(name, each.get(0));
            values.put(name, each);
        }
        Map<String, Args.Value> options = new LinkedHashMap<>();
        for (Map.Entry<String, Args.Value> option : args.options().entrySet()) {
            options.put(option.getKey(), resolve(option.getValue(), lookup, resolved));
        }
        return new Args(arguments, options, values);
    }

    private static boolean holdsReferences(Args args) {
        for (String name : args.arguments().keySet()) {
            if (args.values(name).stream().anyMatch(References::isReference)) {
                return true;
            }
        }
        return args.options().values().stream().anyMatch(References::isReference);
    }

    private static boolean isReference(Args.Value value) {
        return value.type() == ArgType.ENTITY && Lexicon.isEntityName(value.text())
            || value.type() == ArgType.SUB_LEVEL;
    }

    /** One look-up per name and command, so a name given twice means one thing. */
    private static Args.Value resolve(Args.Value value, Lookup lookup, Map<String, String> resolved)
        throws CommandException {
        if (!isReference(value)) {
            return value;
        }
        String text = resolved.get(value.text());
        if (text == null) {
            text = value.type() == ArgType.ENTITY ? entity(value.text(), lookup)
                : SUB + lookup.resolveSubLevel(value.text().substring(SUB.length()));
            resolved.put(value.text(), text);
        }
        return new Args.Value(text, value.type());
    }

    private static String entity(String name, Lookup lookup) throws CommandException {
        OptionalInt found = lookup.resolveEntity(name.substring(1));
        if (found.isEmpty()) {
            throw new CommandException("no_entity", name);
        }
        return "e" + found.getAsInt();
    }
}

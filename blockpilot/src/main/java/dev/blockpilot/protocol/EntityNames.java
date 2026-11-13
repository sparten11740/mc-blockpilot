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

/**
 * Turns the {@code @<name>} entities of a command's arguments into {@code e<id>} just before its handler runs, so
 * handlers only ever see ids. Free text keeps its {@code @}: only values taken as an entity are looked up.
 */
final class EntityNames {
    private EntityNames() {
    }

    /** The arguments with every entity name looked up through the game; the same arguments when they name none. */
    static Args resolve(Args args, GameBridge game) throws CommandException {
        if (!names(args)) {
            return args;
        }
        Map<String, String> ids = new HashMap<>();
        Map<String, Args.Value> arguments = new LinkedHashMap<>();
        Map<String, List<Args.Value>> values = new LinkedHashMap<>();
        for (String name : args.arguments().keySet()) {
            List<Args.Value> resolved = new ArrayList<>();
            for (Args.Value value : args.values(name)) {
                resolved.add(resolve(value, game, ids));
            }
            // Every argument has a value, and the accessors read its first.
            arguments.put(name, resolved.get(0));
            values.put(name, resolved);
        }
        Map<String, Args.Value> options = new LinkedHashMap<>();
        for (Map.Entry<String, Args.Value> option : args.options().entrySet()) {
            options.put(option.getKey(), resolve(option.getValue(), game, ids));
        }
        return new Args(arguments, options, values);
    }

    private static boolean names(Args args) {
        for (String name : args.arguments().keySet()) {
            if (args.values(name).stream().anyMatch(EntityNames::isName)) {
                return true;
            }
        }
        return args.options().values().stream().anyMatch(EntityNames::isName);
    }

    private static boolean isName(Args.Value value) {
        return value.type() == ArgType.ENTITY && Lexicon.isEntityName(value.text());
    }

    /** One look-up per name and command, so a name given twice means one entity. */
    private static Args.Value resolve(Args.Value value, GameBridge game, Map<String, String> ids)
        throws CommandException {
        if (!isName(value)) {
            return value;
        }
        String id = ids.get(value.text());
        if (id == null) {
            OptionalInt found = game.resolveEntity(value.text().substring(1));
            if (found.isEmpty()) {
                throw new CommandException("no_entity", value.text());
            }
            id = "e" + found.getAsInt();
            ids.put(value.text(), id);
        }
        return new Args.Value(id, ArgType.ENTITY);
    }
}

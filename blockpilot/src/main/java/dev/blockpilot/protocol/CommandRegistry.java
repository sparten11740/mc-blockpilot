package dev.blockpilot.protocol;

import dev.blockpilot.api.BlockpilotApi;
import dev.blockpilot.api.CommandException;
import dev.blockpilot.api.CommandSpec;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Every command Blockpilot answers: the built-in ones by bare name, extensions as {@code <namespace>:<name>}. */
public final class CommandRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger("Blockpilot");

    /** A resolved command: the name it is called by and its spec. */
    public record Entry(String qualifiedName, String namespace, String registrant, CommandSpec spec) {
    }

    private final Map<String, Entry> commands = new LinkedHashMap<>();
    private final Map<String, List<Entry>> namespaces = new TreeMap<>();

    public void register(CommandSpec spec) {
        String problem = exampleProblem(spec);
        if (problem != null) {
            throw new IllegalStateException("Example of built-in command " + spec.name() + " does not parse: "
                + problem);
        }
        if (commands.putIfAbsent(spec.name(), new Entry(spec.name(), "", "blockpilot", spec)) != null) {
            throw new IllegalStateException("Built-in command registered twice: " + spec.name());
        }
    }

    /** Null when the spec's example parses against its own grammar, else what is wrong with it. */
    static String exampleProblem(CommandSpec spec) {
        if (spec.example() == null) {
            return null;
        }
        try {
            ArgParser.parse(spec, RawCommand.of(spec.example()));
            return null;
        } catch (CommandException e) {
            return e.getMessage();
        }
    }

    /** Adds extension commands; the first registration of a name wins, later ones are logged and ignored. */
    public void addExtensions(Collection<BlockpilotApi.Registration> registrations) {
        for (BlockpilotApi.Registration registration : registrations) {
            String name = registration.qualifiedName();
            Entry existing = commands.get(name);
            if (existing != null) {
                LOGGER.error("Command {} is registered by both {} and {}; keeping the one from {}", name,
                    existing.registrant(), registration.registrant(), existing.registrant());
                continue;
            }
            String problem = exampleProblem(registration.spec());
            if (problem != null) {
                LOGGER.warn("Example of {} (from {}) does not parse: {}", name, registration.registrant(), problem);
            }
            Entry entry = new Entry(name, registration.namespace(), registration.registrant(), registration.spec());
            commands.put(name, entry);
            namespaces.computeIfAbsent(registration.namespace(), key -> new ArrayList<>()).add(entry);
        }
    }

    public Entry resolve(String name) {
        return commands.get(name);
    }

    public Collection<Entry> all() {
        return commands.values();
    }

    public List<Entry> namespace(String namespace) {
        return namespaces.getOrDefault(namespace, List.of());
    }

    public Collection<String> namespaces() {
        return namespaces.keySet();
    }
}

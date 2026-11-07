package dev.blockpilot.protocol;

import dev.blockpilot.api.BlockpilotApi;
import dev.blockpilot.api.CommandSpec;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Every command Blockpilot answers: the built-in ones by bare name, extensions as {@code <namespace>:<name>}. */
public final class CommandRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger("Blockpilot");

    /** A resolved command: the name it is called by and its spec. */
    public record Entry(String qualifiedName, String namespace, String registrant, CommandSpec spec) {
    }

    private final Map<String, Entry> commands = new LinkedHashMap<>();
    /** The registrant of Blockpilot's own commands, compat modules' included. */
    static final String BUILT_IN = "blockpilot";

    private final Map<String, List<Entry>> namespaces = new TreeMap<>();
    private final Map<String, String> disabled = new ConcurrentHashMap<>();

    public void register(CommandSpec spec) {
        spec.freeze();
        Entry entry = new Entry(spec.name(), "", "blockpilot", spec);
        String problem = exampleProblem(entry);
        if (problem != null) {
            throw new IllegalStateException("Example of built-in command " + spec.name() + " does not parse: "
                + problem);
        }
        if (commands.putIfAbsent(spec.name(), entry) != null) {
            throw new IllegalStateException("Built-in command registered twice: " + spec.name());
        }
    }

    /** Null when the command's example parses against its own grammar, else what is wrong with it. */
    static String exampleProblem(Entry entry) {
        if (entry.spec().example() == null) {
            return null;
        }
        try {
            Parser.example(entry, entry.spec().example());
            return null;
        } catch (Parser.SyntaxError e) {
            return e.getMessage();
        }
    }

    /**
     * Adds extension commands; the first registration of a name wins, later ones are logged and ignored, and so is
     * one whose example doesn't parse.
     */
    public void addExtensions(Collection<BlockpilotApi.Registration> registrations) {
        for (BlockpilotApi.Registration registration : registrations) {
            String name = registration.qualifiedName();
            Entry existing = commands.get(name);
            if (existing != null) {
                LOGGER.error("Command {} is registered by both {} and {}; keeping the one from {}", name,
                    existing.registrant(), registration.registrant(), existing.registrant());
                continue;
            }
            Entry entry = new Entry(name, registration.namespace(), registration.registrant(), registration.spec());
            String problem = exampleProblem(entry);
            if (problem != null) {
                LOGGER.error("Example of {} (from {}) does not parse, so the command is ignored: {}", name,
                    registration.registrant(), problem);
                continue;
            }
            commands.put(name, entry);
            namespaces.computeIfAbsent(registration.namespace(), key -> new ArrayList<>()).add(entry);
        }
    }

    /** Hides a namespace's commands until restart; {@code help <namespace>} then gives the reason. */
    public void disable(String namespace, String reason) {
        disabled.put(namespace, reason);
    }

    /** Null unless the namespace is disabled. */
    public String disabledReason(String namespace) {
        return disabled.get(namespace);
    }

    public Entry resolve(String name) {
        Entry entry = commands.get(name);
        return entry == null || disabled.containsKey(entry.namespace()) ? null : entry;
    }

    public Collection<Entry> all() {
        return commands.values().stream().filter(entry -> !disabled.containsKey(entry.namespace())).toList();
    }

    public List<Entry> namespace(String namespace) {
        return disabled.containsKey(namespace) ? List.of() : namespaces.getOrDefault(namespace, List.of());
    }

    /** Whether Blockpilot itself registered the namespace's commands, as its compat modules do, so they need no allowing. */
    public boolean builtIn(String namespace) {
        return namespace.isEmpty() || namespaces.getOrDefault(namespace, List.of()).stream()
            .anyMatch(entry -> entry.registrant().equals(BUILT_IN));
    }

    public Collection<String> namespaces() {
        return namespaces.keySet().stream().filter(namespace -> !disabled.containsKey(namespace)).toList();
    }
}

package dev.blockpilot.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The entry point for other mods. Register commands at any time during mod loading (before or after Blockpilot's
 * own start-up); Blockpilot reads the registrations once loading completes. Keep the calls in a class that is only
 * touched after checking that the {@code blockpilot} mod is loaded, so the integrating mod runs without Blockpilot.
 */
public final class BlockpilotApi {
    /** The API version; additions bump the minor version, breaking changes the major one. */
    public static final String VERSION = "2.1.0";

    private static final Logger LOGGER = LoggerFactory.getLogger("Blockpilot API");

    /** One registration: the namespace it is called under and the mod that made it. */
    public record Registration(String namespace, String registrant, CommandSpec spec) {
        public String qualifiedName() {
            return namespace + ":" + spec.name();
        }
    }

    private static final List<Registration> REGISTRATIONS = new ArrayList<>();
    private static volatile Consumer<Event> eventSink = event -> { };
    private static boolean frozen;

    private BlockpilotApi() {
    }

    /**
     * Registers a command, invoked as {@code <namespace>:<name>}. The namespace is normally the registering mod's
     * id; a compat module registers under the id of the mod it integrates and passes its own id as registrant.
     * A declaration the grammar can't reach throws {@link IllegalArgumentException}, and the spec can't change
     * afterwards.
     */
    public static synchronized void register(String namespace, String registrant, CommandSpec spec) {
        if (namespace == null || !Lexicon.isNamespace(namespace)) {
            throw new IllegalArgumentException("a namespace is [a-z0-9_.-]+, not " + namespace);
        }
        spec.freeze();
        if (frozen) {
            LOGGER.error("{} registered {}:{} after mod loading finished; it is ignored", registrant, namespace,
                spec.name());
            return;
        }
        REGISTRATIONS.add(new Registration(namespace, registrant, spec));
    }

    /** As {@link #register(String, String, CommandSpec)}, for a mod registering under its own id. */
    public static void register(String modId, CommandSpec spec) {
        register(modId, modId, spec);
    }

    /** Emits an event to every session, delivered as {@code ! <namespace>:<name> <fields>}. */
    public static void emit(String namespace, String name, Fields fields) {
        eventSink.accept(Event.of(namespace + ":" + name, fields));
    }

    /** Blockpilot's side: takes the registrations once loading completes; later ones are rejected. */
    public static synchronized List<Registration> freeze(Consumer<Event> sink) {
        frozen = true;
        eventSink = sink;
        return Collections.unmodifiableList(new ArrayList<>(REGISTRATIONS));
    }
}

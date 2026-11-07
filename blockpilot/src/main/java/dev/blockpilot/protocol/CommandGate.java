package dev.blockpilot.protocol;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * The commands a script may not run, judged before any step does: another mod's commands until its namespace is in
 * allowedExtensions, and for a play-only agent the commands that reach beyond playing as a player (slash commands,
 * the integrated server, world files, mod configs, files written to disk, switching worlds or servers, quitting),
 * extension commands that run on the integrated server among them.
 */
final class CommandGate {
    private static final Set<String> REFUSED = Set.of("cmd", "server", "nbt", "tick", "gametest", "snapshot",
        "config", "record", "perf", "load", "create", "delete", "join", "leave", "quit");
    /** How a refusal names a command whose first word alone doesn't say what it does. */
    private static final Map<String, String> SHOWN = Map.of("create", "create world", "delete", "delete world");

    private final boolean playOnly;
    private final Predicate<CommandRegistry.Entry> usable;

    CommandGate(boolean playOnly, Predicate<CommandRegistry.Entry> usable) {
        this.playOnly = playOnly;
        this.usable = usable;
    }

    /** The first refused command in the step, nested ones included, as the refusal names it; null when none. */
    String refusal(Statement statement) {
        return switch (statement) {
            case Statement.Call call -> refusal(call);
            case Statement.Assert check -> refusal(check.query());
            case Statement.If check -> first(Stream.of(check.query(), check.then()));
            case Statement.WaitUntil wait -> refusal(wait.query());
            case Statement.Sample sample -> first(Stream.concat(sample.queries().stream(),
                Stream.ofNullable(sample.during())));
            case Statement.WaitTicks wait -> null;
            case Statement.WaitIdle wait -> null;
        };
    }

    private String first(Stream<? extends Statement> statements) {
        return statements.map(this::refusal).filter(Objects::nonNull).findFirst().orElse(null);
    }

    private String refusal(Statement.Call call) {
        CommandRegistry.Entry entry = call.entry();
        String name = entry.spec().name();
        if (!entry.namespace().isEmpty()) {
            // On the integrated server an extension command reaches beyond the player, as server does.
            boolean refused = !usable.test(entry) || playOnly && entry.spec().runsOnServer();
            return refused ? entry.qualifiedName() : null;
        }
        if (!playOnly || !REFUSED.contains(name)) {
            return null;
        }
        // Reading how a capture goes writes nothing.
        if (name.equals("perf") && call.args().has("op") && call.args().word("op").equals("status")) {
            return null;
        }
        return SHOWN.getOrDefault(name, name);
    }
}

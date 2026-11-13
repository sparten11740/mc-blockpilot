package dev.blockpilot.protocol;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/**
 * The commands a play-only agent's script may not run, judged before any step does: another mod's commands until its
 * namespace is in allowedExtensions, slash commands whose root isn't in playSlashCommands, and the commands that reach
 * beyond playing as a player (the integrated server, world files, mod configs, files written to disk, switching
 * worlds or servers, quitting), extension commands that run on the integrated server among them.
 */
final class CommandGate {
    private static final Set<String> REFUSED = Set.of("server", "nbt", "tick", "gametest", "snapshot",
        "config", "record", "perf", "load", "create", "delete", "join", "leave", "quit");
    /** How a refusal names a command whose first word alone doesn't say what it does. */
    private static final Map<String, String> SHOWN = Map.of("create", "create world", "delete", "delete world");

    private final SafetyPolicy policy;

    /** One policy for the whole script, as it stood when the script came in. */
    CommandGate(SafetyPolicy policy) {
        this.policy = policy;
    }

    /** The first refused command in the step, nested ones included, as the refusal names it; null when none. */
    String refusal(Statement statement) {
        if (statement instanceof Statement.Call call) {
            return refusal(call);
        }
        if (statement instanceof Statement.Assert check) {
            return refusal(check.query());
        }
        if (statement instanceof Statement.If check) {
            return first(Stream.of(check.query(), check.then()));
        }
        if (statement instanceof Statement.WaitUntil wait) {
            return refusal(wait.query());
        }
        if (statement instanceof Statement.Sample sample) {
            return first(Stream.concat(sample.queries().stream(), Stream.ofNullable(sample.during())));
        }
        return null;
    }

    private String first(Stream<? extends Statement> statements) {
        return statements.map(this::refusal).filter(Objects::nonNull).findFirst().orElse(null);
    }

    private String refusal(Statement.Call call) {
        CommandRegistry.Entry entry = call.entry();
        String name = entry.spec().name();
        if (!entry.namespace().isEmpty()) {
            // On the integrated server an extension command reaches beyond the player, as server does.
            boolean refused = !policy.extensionAllowed(entry) || policy.playOnly() && entry.spec().runsOnServer();
            return refused ? entry.qualifiedName() : null;
        }
        if (name.equals("cmd")) {
            String root = SafetyPolicy.rootOf(call.args().text("command"));
            return policy.slashCommandAllowed(root) ? null : "cmd " + root;
        }
        if (!policy.playOnly() || !REFUSED.contains(name)) {
            return null;
        }
        // Reading how a capture goes writes nothing.
        if (name.equals("perf") && call.args().has("op") && call.args().word("op").equals("status")) {
            return null;
        }
        return SHOWN.getOrDefault(name, name);
    }
}

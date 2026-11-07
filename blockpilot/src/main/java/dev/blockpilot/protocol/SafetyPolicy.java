package dev.blockpilot.protocol;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Every agent safety decision, answered by one immutable policy that is swapped whole when the config file changes,
 * so no check can see half of a change. A new check is a new method here, which both implementations must answer.
 */
public sealed interface SafetyPolicy permits ConfiguredPolicy, AllowAllPolicy {
    boolean trusts(UUID player, Connection now);

    boolean extensionAllowed(CommandRegistry.Entry entry);

    /** Whether an extension's event may reach agents, besides those of Blockpilot's own compat namespaces. */
    boolean eventAllowed(String namespace);

    boolean playOnly();

    /** What the agent indicator adds, if anything. */
    String indicatorNote();

    /** The trusted players as the config file names them; none to name while everyone counts as trusted. */
    List<String> trustedNames();

    /** {@code trustUnverified}: a development run's switch that applies the list on unverified servers too. */
    static SafetyPolicy from(Config config, boolean trustUnverified) {
        return config.dangerouslyAllowAll ? AllowAllPolicy.INSTANCE : new ConfiguredPolicy(config.trustedPlayers,
            Set.copyOf(config.allowedExtensions), !config.agentCommands.equals("all"), trustUnverified);
    }
}

package dev.blockpilot.protocol;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The safety the config file sets: trusted players by name ignoring case, never on an unverified server unless a
 * development run says so; other mods' commands and events by namespace; and the play profile, under which slash
 * commands need their root allowed.
 */
public record ConfiguredPolicy(List<String> trustedNames, Set<String> allowedExtensions, boolean playOnly,
    boolean trustUnverified, List<String> playSlashCommands) implements SafetyPolicy {
    /** Nobody trusted, no extension allowed, every command: a config file's defaults. */
    public static final ConfiguredPolicy DEFAULT = new ConfiguredPolicy(List.of(), Set.of(), false, false, List.of());

    public ConfiguredPolicy {
        trustedNames = List.copyOf(trustedNames);
        allowedExtensions = Set.copyOf(allowedExtensions);
        playSlashCommands = List.copyOf(playSlashCommands);
    }

    @Override
    public boolean trusts(UUID player, Connection now) {
        if (player != null && player.equals(now.self())) {
            return true;
        }
        String name = now.nameOf(player);
        return (!now.unverified() || trustUnverified) && name != null
            && trustedNames.stream().anyMatch(name::equalsIgnoreCase);
    }

    @Override
    public boolean extensionAllowed(CommandRegistry.Entry entry) {
        return entry.builtIn() || allowedExtensions.contains(entry.namespace());
    }

    @Override
    public boolean eventAllowed(String namespace) {
        return allowedExtensions.contains(namespace);
    }

    @Override
    public boolean slashCommandAllowed(String root) {
        return !playOnly || playSlashCommands.stream().anyMatch(root::equalsIgnoreCase);
    }

    @Override
    public String indicatorNote() {
        return "";
    }
}

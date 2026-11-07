package dev.blockpilot.game.safety;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Whom agents take words from: the players a person trusted, by name ignoring case, and the agent's own player. On a
 * server that doesn't verify accounts nobody else counts, as anyone can take any name there. Read from any thread;
 * changed on the client thread.
 */
public final class Trust {
    private volatile List<String> names;
    private volatile Set<String> keys;
    private volatile UUID self;
    private volatile boolean unverified;
    /** The players the client knows, by UUID, so a UUID can be judged off the client thread. */
    private volatile Map<UUID, String> players = Map.of();

    public Trust(Collection<String> trusted) {
        setNames(new ArrayList<>(trusted));
    }

    public boolean trusts(UUID player, String name) {
        if (player != null && player.equals(self)) {
            return true;
        }
        return !unverified && name != null && keys.contains(key(name));
    }

    public boolean trusts(UUID player) {
        return player != null && trusts(player, players.get(player));
    }

    /** What agents get in place of a player's chat: null to pass it as it came, else its stand-in text. */
    public String withheld(UUID sender, String name) {
        return trusts(sender, name) ? null : "withheld from=" + (name != null ? name : sender);
    }

    /** The list as the config file has it now. */
    public void set(Collection<String> trusted) {
        setNames(new ArrayList<>(trusted));
    }

    /** As the person wrote them. */
    public List<String> names() {
        return names;
    }

    public boolean unverified() {
        return unverified;
    }

    /** Whether the named player is among those the client knows. */
    public boolean online(String name) {
        return players.values().stream().anyMatch(player -> key(player).equals(key(name)));
    }

    public String nameOf(UUID player) {
        return player != null ? players.get(player) : null;
    }

    /** What the client knows about the connection: the agent's own player, whether accounts are verified, who is on. */
    void update(UUID ownPlayer, boolean accountsUnverified, Map<UUID, String> knownPlayers) {
        self = ownPlayer;
        unverified = accountsUnverified;
        players = Map.copyOf(knownPlayers);
    }

    /**
     * Whether the server gave the player the UUID it makes up for an account it didn't check, as a server in offline
     * mode does for everyone.
     */
    static boolean offlineAccount(UUID player, String name) {
        return player.equals(UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8)));
    }

    private void setNames(List<String> changed) {
        names = List.copyOf(changed);
        keys = changed.stream().map(Trust::key).collect(Collectors.toUnmodifiableSet());
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}

package dev.blockpilot.game.safety;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Whom agents take words from: the players a person trusted, by name ignoring case, and the agent's own player. On a
 * server that doesn't verify accounts nobody else counts, as anyone can take any name there. Read from any thread
 * (the integrated server's and extensions' too), so each state is one immutable snapshot swapped whole.
 */
public final class Trust {
    private record Names(List<String> written, Set<String> keys) {
        static Names of(Collection<String> names) {
            Set<String> keys = names.stream().map(Trust::key).collect(Collectors.toUnmodifiableSet());
            return new Names(List.copyOf(names), keys);
        }
    }

    /** What the client knows about its connection; {@code players}: the players it knows, by UUID. */
    private record Connection(UUID self, boolean unverified, Map<UUID, String> players) {
    }

    private volatile Names names;
    private volatile Connection connection = new Connection(null, false, Map.of());
    /** A development run's switch: the list applies on unverified servers too, for offline dev accounts. */
    private final boolean trustUnverified;
    /** dangerouslyAllowAll: everyone counts as trusted. */
    private volatile boolean allowAll;

    public Trust(Collection<String> trusted, boolean trustUnverified) {
        this.trustUnverified = trustUnverified;
        this.names = Names.of(trusted);
    }

    public boolean trusts(UUID player, String name) {
        return trusts(connection, player, name);
    }

    public boolean trusts(UUID player) {
        Connection now = connection;
        return player != null && trusts(now, player, now.players().get(player));
    }

    private boolean trusts(Connection now, UUID player, String name) {
        if (allowAll || player != null && player.equals(now.self())) {
            return true;
        }
        return (!now.unverified() || trustUnverified) && name != null && names.keys().contains(key(name));
    }

    /** What agents get in place of a player's chat: null to pass it as it came, else its stand-in text. */
    public String withheld(UUID sender, String name) {
        return trusts(sender, name) ? null : "withheld from=" + (name != null ? name : sender);
    }

    /** The list as the config file has it now. */
    public void set(Collection<String> trusted) {
        names = Names.of(trusted);
    }

    /** As the person wrote them. */
    public List<String> names() {
        return names.written();
    }

    public boolean unverified() {
        return connection.unverified();
    }

    public boolean trustUnverified() {
        return trustUnverified;
    }

    public boolean allowAll() {
        return allowAll;
    }

    public void setAllowAll(boolean value) {
        allowAll = value;
    }

    /** Whether the named player is among those the client knows. */
    public boolean online(String name) {
        return connection.players().values().stream().anyMatch(player -> key(player).equals(key(name)));
    }

    public String nameOf(UUID player) {
        return player != null ? connection.players().get(player) : null;
    }

    /** What the client knows about the connection: the agent's own player, whether accounts are verified, who is on. */
    void update(UUID ownPlayer, boolean accountsUnverified, Map<UUID, String> knownPlayers) {
        connection = new Connection(ownPlayer, accountsUnverified, Map.copyOf(knownPlayers));
    }

    /**
     * Whether the server gave the player the UUID it makes up for an account it didn't check, as a server in offline
     * mode does for everyone.
     */
    static boolean offlineAccount(UUID player, String name) {
        return player.equals(UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8)));
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}

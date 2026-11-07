package dev.blockpilot.protocol;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * What the client knows about its connection, one immutable snapshot: the agent's own player, whether the server
 * verifies accounts, and the players it knows by UUID.
 */
public record Connection(UUID self, boolean unverified, Map<UUID, String> players) {
    public static final Connection NONE = new Connection(null, false, Map.of());

    public Connection {
        players = Map.copyOf(players);
    }

    public String nameOf(UUID player) {
        return player != null ? players.get(player) : null;
    }

    public boolean online(String name) {
        return players.values().stream().anyMatch(player -> player.equalsIgnoreCase(name));
    }

    /** The same connection knowing one more player by name, as the integrated server knows a sender. */
    public Connection with(UUID player, String name) {
        if (player == null || name == null) {
            return this;
        }
        Map<UUID, String> known = new HashMap<>(players);
        known.put(player, name);
        return new Connection(self, unverified, known);
    }
}

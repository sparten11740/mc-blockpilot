package dev.blockpilot.game.safety;

import dev.blockpilot.protocol.Connection;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

/**
 * The connection facts the safety policy judges by, as one immutable snapshot swapped whole, since the integrated
 * server's thread and extensions read it too.
 */
public final class Trust {
    private volatile Connection connection = Connection.NONE;

    public Connection connection() {
        return connection;
    }

    void update(UUID ownPlayer, boolean accountsUnverified, Map<UUID, String> knownPlayers) {
        connection = new Connection(ownPlayer, accountsUnverified, knownPlayers);
    }

    /** The text agents get in place of an untrusted player's chat. */
    static String withheld(UUID sender, String name) {
        return "withheld from=" + (name != null ? name : sender);
    }

    /**
     * Whether the server gave the player the UUID it makes up for an account it didn't check, as a server in offline
     * mode does for everyone.
     */
    static boolean offlineAccount(UUID player, String name) {
        return player.equals(UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8)));
    }
}

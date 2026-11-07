package dev.blockpilot.platform.vanilla;

import java.util.UUID;

/**
 * The sender of the player chat line being shown: the chat listener knows it, the chat window that adds the line
 * doesn't. Null outside a player's line, as for disguised chat. Client thread only.
 */
public final class VanillaChat {
    public static UUID sender;

    private VanillaChat() {
    }
}

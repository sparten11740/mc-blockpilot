package dev.blockpilot.game.safety;

import dev.blockpilot.game.common.ClientScreens;
import dev.blockpilot.game.input.Injection;
import dev.blockpilot.platform.ClientPlatform;
import dev.blockpilot.protocol.Refusal;
import java.util.function.BooleanSupplier;
import net.minecraft.network.chat.Component;

/**
 * Slash commands on their way to the server, for the play profile: what the agent typed into the chat screen or
 * clicked in a chat message doesn't go out. Client thread only.
 */
public final class SlashCommands {
    private static BooleanSupplier playOnly = () -> false;

    private SlashCommands() {
    }

    public static void install(BooleanSupplier playOnlyProfile) {
        playOnly = playOnlyProfile;
    }

    /** True when the command must not be sent: under play, one with the agent's input behind it. */
    public static boolean refuse(String command) {
        boolean injected = Injection.active();
        if (!playOnly.getAsBoolean() || !(injected || Injection.reachedChat(ClientScreens.current()))) {
            return false;
        }
        if (injected) {
            Refusal.raise("not_allowed", "cmd");
        } else {
            ClientPlatform.showLocal(Component.literal("Blockpilot didn't send /" + command
                + ": an agent typed in this chat, and agents may only play. Open the chat again to send it."));
        }
        return true;
    }
}

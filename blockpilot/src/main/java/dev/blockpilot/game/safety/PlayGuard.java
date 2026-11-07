package dev.blockpilot.game.safety;

import dev.blockpilot.game.common.ClientScreens;
import dev.blockpilot.game.input.Injection;
import dev.blockpilot.platform.ClientPlatform;
import dev.blockpilot.protocol.Refusal;
import java.util.function.BooleanSupplier;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.network.chat.Component;

/**
 * What a play-only agent can't do through the client itself, beyond the commands the engine refuses: send slash
 * commands it typed or clicked, use screens other than gameplay's, and follow links. Client thread only.
 */
public final class PlayGuard {
    private static BooleanSupplier playOnly = () -> false;

    private PlayGuard() {
    }

    public static void install(BooleanSupplier playOnlyProfile) {
        playOnly = playOnlyProfile;
        Injection.setScreenGate(PlayGuard::inputAllowed);
    }

    /**
     * Whether Blockpilot's input may reach the screen: under play, only gameplay's, so that the pause menu, the title
     * screen's server and world lists, the options and confirm screens stay out of reach. A list of what is allowed,
     * so a screen nobody thought of is refused.
     */
    static boolean inputAllowed(Screen screen) {
        return !playOnly.getAsBoolean() || screen == null || screen instanceof AbstractContainerScreen<?>
            || screen instanceof ChatScreen || screen instanceof AbstractSignEditScreen
            || screen instanceof BookEditScreen || screen instanceof BookViewScreen;
    }

    /** True when a click that opens a link or a file, or copies to the clipboard, must not happen: under play, ours. */
    public static boolean refuseLink() {
        if (!playOnly.getAsBoolean() || !Injection.active()) {
            return false;
        }
        Refusal.raise("not_allowed", "link");
        return true;
    }

    /** True when the command must not be sent: under play, one with the agent's input behind it. */
    public static boolean refuseCommand(String command) {
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

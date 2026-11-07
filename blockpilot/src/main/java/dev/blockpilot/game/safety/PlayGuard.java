package dev.blockpilot.game.safety;

import dev.blockpilot.game.common.ClientScreens;
import dev.blockpilot.game.input.Injection;
import dev.blockpilot.platform.ClientPlatform;
import dev.blockpilot.protocol.ConfiguredPolicy;
import dev.blockpilot.protocol.Refusal;
import dev.blockpilot.protocol.SafetyPolicy;
import java.util.function.Supplier;
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
    private static Supplier<SafetyPolicy> policy = () -> ConfiguredPolicy.DEFAULT;

    private PlayGuard() {
    }

    public static void install(Supplier<SafetyPolicy> safetyPolicy) {
        policy = safetyPolicy;
        Injection.setScreenGate(PlayGuard::inputAllowed);
    }

    /**
     * Whether Blockpilot's input may reach the screen: under play, only gameplay's, so that the pause menu, the title
     * screen's server and world lists, the options and confirm screens stay out of reach. A list of what is allowed,
     * so a screen nobody thought of is refused.
     */
    static boolean inputAllowed(Screen screen) {
        return !policy.get().playOnly() || screen == null || screen instanceof AbstractContainerScreen<?>
            || screen instanceof ChatScreen || screen instanceof AbstractSignEditScreen
            || screen instanceof BookEditScreen || screen instanceof BookViewScreen;
    }

    /** True when a click that opens a link or a file, or copies to the clipboard, must not happen: under play, ours. */
    public static boolean refuseLink() {
        if (!policy.get().playOnly() || !Injection.active()) {
            return false;
        }
        Refusal.raise("not_allowed", "link");
        return true;
    }

    /** True when the command must not be sent: one with the agent's input behind it whose root the policy refuses. */
    public static boolean refuseCommand(String command) {
        boolean injected = Injection.active();
        String root = SafetyPolicy.rootOf(command);
        if (!(injected || Injection.reachedChat(ClientScreens.current())) || policy.get().slashCommandAllowed(root)) {
            return false;
        }
        if (injected) {
            Refusal.raise("not_allowed", "cmd " + root);
        } else {
            ClientPlatform.showLocal(Component.literal("Blockpilot didn't send /" + command
                + ": an agent typed in this chat, and agents may only play. Open the chat again to send it."));
        }
        return true;
    }
}

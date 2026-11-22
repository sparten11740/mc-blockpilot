package dev.blockpilot.platform.vanilla.mixin;

import dev.blockpilot.platform.ClientPlatform;
import dev.blockpilot.platform.vanilla.VanillaChat;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Player and disguised chat lines as they are shown, decorated with their sender, which {@link ChatListenerMixin} names
 * while it shows a player's line. An untrusted player's line is swapped for its stand-in before the window shows it, so
 * the common chat window mixin logs the stand-in.
 */
@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {
    @ModifyVariable(method = "addPlayerMessage", at = @At("HEAD"), argsOnly = true)
    private Component blockpilot$chat(Component message) {
        return ClientPlatform.hooks().judgeChat(message, VanillaChat.sender);
    }
}

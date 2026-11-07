package dev.blockpilot.platform.vanilla.mixin;

import dev.blockpilot.platform.ClientPlatform;
import dev.blockpilot.platform.vanilla.VanillaChat;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Chat messages as they are shown, decorated with their sender. Only the chat listener adds them, for player and
 * disguised chat alike, and names the player while it shows a player's line; system messages come through
 * {@link ChatListenerMixin}. An untrusted player's line is swapped for its stand-in before the window shows or logs it.
 */
@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {
    @ModifyVariable(method = "addPlayerMessage", at = @At("HEAD"), argsOnly = true)
    private Component blockpilot$chat(Component message) {
        Component judged = ClientPlatform.hooks().judgeChat(message, VanillaChat.sender);
        ClientPlatform.hooks().onChatReceived(judged);
        return judged;
    }
}

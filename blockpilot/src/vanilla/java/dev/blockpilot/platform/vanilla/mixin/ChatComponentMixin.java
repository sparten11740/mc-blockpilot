package dev.blockpilot.platform.vanilla.mixin;

import dev.blockpilot.platform.ClientPlatform;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Chat messages as they are shown, decorated with their sender. Only the chat listener adds them, for player and
 * disguised chat alike; system messages come through {@link ChatListenerMixin}.
 */
@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {
    @Inject(method = "addPlayerMessage", at = @At("HEAD"))
    private void blockpilot$chat(Component message, MessageSignature signature, GuiMessageTag tag, CallbackInfo info) {
        ClientPlatform.hooks().onChatReceived(message);
    }
}

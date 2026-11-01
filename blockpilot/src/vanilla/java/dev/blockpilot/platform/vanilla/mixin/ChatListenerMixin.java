package dev.blockpilot.platform.vanilla.mixin;

import dev.blockpilot.platform.ClientPlatform;
import net.minecraft.client.multiplayer.chat.ChatListener;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** System messages for the chat, such as command feedback; action-bar messages arrive separately. */
@Mixin(ChatListener.class)
public abstract class ChatListenerMixin {
    @Inject(method = "handleSystemMessage", at = @At("HEAD"))
    private void blockpilot$systemMessage(Component message, boolean remote, CallbackInfo info) {
        ClientPlatform.hooks().onChatReceived(message);
    }
}

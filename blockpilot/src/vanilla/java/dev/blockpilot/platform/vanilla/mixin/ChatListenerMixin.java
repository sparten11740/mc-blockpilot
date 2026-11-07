package dev.blockpilot.platform.vanilla.mixin;

import com.mojang.authlib.GameProfile;
import dev.blockpilot.platform.ClientPlatform;
import dev.blockpilot.platform.vanilla.VanillaChat;
import java.time.Instant;
import net.minecraft.client.multiplayer.chat.ChatListener;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * System messages for the chat, such as command feedback (action-bar messages arrive separately), and the sender of
 * each player chat line while it is shown, for {@link ChatComponentMixin}.
 */
@Mixin(ChatListener.class)
public abstract class ChatListenerMixin {
    @Inject(method = "handleSystemMessage", at = @At("HEAD"))
    private void blockpilot$systemMessage(Component message, boolean remote, CallbackInfo info) {
        ClientPlatform.hooks().onChatReceived(message);
    }

    @Inject(method = "showMessageToPlayer", at = @At("HEAD"))
    private void blockpilot$showing(ChatType.Bound bound, PlayerChatMessage message, Component decorated,
        GameProfile sender, boolean onlyShowSecure, Instant received, CallbackInfoReturnable<Boolean> info) {
        VanillaChat.sender = message.sender();
    }

    @Inject(method = "showMessageToPlayer", at = @At("RETURN"))
    private void blockpilot$shown(ChatType.Bound bound, PlayerChatMessage message, Component decorated,
        GameProfile sender, boolean onlyShowSecure, Instant received, CallbackInfoReturnable<Boolean> info) {
        VanillaChat.sender = null;
    }
}

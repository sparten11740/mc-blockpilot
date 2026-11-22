package dev.blockpilot.mixin;

import dev.blockpilot.platform.ClientPlatform;
import net.minecraft.client.gui.components.ChatComponent;
//? if >=26.1 {
/*import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
*///?} else {
import net.minecraft.client.GuiMessageTag;
//?}
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Every line the chat window adds, once: from the server, and the lines added on the client, such as client-side
 * command replies, which no loader chat event reports. Untrusted chat arrives here already withheld.
 */
@Mixin(ChatComponent.class)
abstract class ChatComponentMixin {
    //? if >=26.1 {
    /*@Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;"
        + "Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;"
        + "Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V", at = @At("HEAD"))
    private void blockpilot$added(Component message, MessageSignature signature, GuiMessageSource source,
        GuiMessageTag tag, CallbackInfo info) {
    *///?} else {
    @Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;"
        + "Lnet/minecraft/client/GuiMessageTag;)V", at = @At("HEAD"))
    private void blockpilot$added(Component message, MessageSignature signature, GuiMessageTag tag,
        CallbackInfo info) {
    //?}
        ClientPlatform.onChatWindowLine(message);
    }
}

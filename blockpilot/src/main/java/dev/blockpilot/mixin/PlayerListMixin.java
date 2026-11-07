package dev.blockpilot.mixin;

import dev.blockpilot.platform.ClientPlatform;
import java.util.function.Predicate;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The integrated server's log line for a player's chat, which the game log agents read would otherwise hold an
 * untrusted player's words: withheld as the client withholds the line itself.
 */
@Mixin(PlayerList.class)
public abstract class PlayerListMixin {
    private static final String BROADCAST = "broadcastChatMessage(Lnet/minecraft/network/chat/PlayerChatMessage;"
        + "Ljava/util/function/Predicate;Lnet/minecraft/server/level/ServerPlayer;"
        + "Lnet/minecraft/network/chat/ChatType$Bound;)V";

    /** The player whose line is being sent; server thread only. */
    @Unique
    private static ServerPlayer blockpilot$sender;

    @Inject(method = BROADCAST, at = @At("HEAD"))
    private void blockpilot$sending(PlayerChatMessage message, Predicate<ServerPlayer> filter, ServerPlayer sender,
        ChatType.Bound bound, CallbackInfo info) {
        blockpilot$sender = sender;
    }

    @ModifyArg(method = BROADCAST, at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;"
        + "logChatMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/ChatType$Bound;"
        + "Ljava/lang/String;)V"), index = 0)
    private Component blockpilot$logged(Component content) {
        ServerPlayer sender = blockpilot$sender;
        blockpilot$sender = null;
        return sender == null ? content
            : ClientPlatform.hooks().judgeServerChat(content, sender.getUUID(), sender.getName().getString());
    }
}

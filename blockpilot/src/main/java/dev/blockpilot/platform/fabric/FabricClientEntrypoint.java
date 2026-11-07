package dev.blockpilot.platform.fabric;

import dev.blockpilot.Blockpilot;
import dev.blockpilot.platform.ClientPlatform;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
//? if >=26.1 {
/*import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
*///?} else {
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
//?}
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
//? if >=26.1 {
/*import net.minecraft.resources.ResourceLocation;
*///?}

/** The mod on Fabric: installs the platform and forwards Fabric API's events to the platform hooks. */
public final class FabricClientEntrypoint implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientPlatform.install(new FabricServices());
        Blockpilot.init();

        ClientLifecycleEvents.CLIENT_STARTED.register(client -> ClientPlatform.hooks().onLoadComplete());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> ClientPlatform.hooks().onClientStopping());
        ClientTickEvents.END_CLIENT_TICK.register(client -> ClientPlatform.hooks().onClientTickEnd());
        //? if >=26.1 {
        /*HudElementRegistry.addLast(ResourceLocation.fromNamespaceAndPath(Blockpilot.MOD_ID, "status"),
            (graphics, deltaTracker) -> ClientPlatform.hooks().onHudRender(graphics));
        *///?} else {
        HudRenderCallback.EVENT.register((graphics, deltaTracker) -> ClientPlatform.hooks().onHudRender(graphics));
        //?}
        // Fabric can't change a chat line, so an untrusted player's is cancelled and its stand-in shown instead, in the
        // window and its log line alike.
        ClientReceiveMessageEvents.ALLOW_CHAT.register((message, signedMessage, sender, params, receptionTimestamp) -> {
            Component judged = ClientPlatform.hooks().judgeChat(message,
                signedMessage != null ? signedMessage.sender() : null);
            if (judged == message) {
                return true;
            }
            //? if >=26.2 {
            /*Minecraft.getInstance().gui.hud.getChat().addClientSystemMessage(judged);
            *///?} else if >=26.1 {
            /*Minecraft.getInstance().gui.getChat().addClientSystemMessage(judged);
            *///?} else {
            Minecraft.getInstance().gui.getChat().addMessage(judged);
            //?}
            ClientPlatform.hooks().onChatReceived(judged);
            return false;
        });
        ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, receptionTimestamp) ->
            ClientPlatform.hooks().onChatReceived(message));
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) {
                ClientPlatform.hooks().onChatReceived(message);
            }
        });
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> ClientPlatform.hooks().onScreenInit(screen));
        ServerLifecycleEvents.SERVER_STARTED.register(server -> ClientPlatform.hooks().onServerStarted(server));
        ServerTickEvents.END_SERVER_TICK.register(server -> ClientPlatform.hooks().onServerTickEnd(server));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> ClientPlatform.hooks().onServerStopping(server));
        // Fabric fires the load for every chunk that becomes full, and the generate besides for a new one.
        //? if >=26.1 {
        /*ServerChunkEvents.CHUNK_LOAD.register((level, chunk, generated) ->
            ClientPlatform.hooks().onServerChunkLoaded());
        *///?} else {
        ServerChunkEvents.CHUNK_LOAD.register((level, chunk) -> ClientPlatform.hooks().onServerChunkLoaded());
        //?}
        ServerChunkEvents.CHUNK_GENERATE.register((level, chunk) -> ClientPlatform.hooks().onServerChunkGenerated());
    }
}

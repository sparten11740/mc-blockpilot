package dev.blockpilot.platform.fabric;

import dev.blockpilot.Blockpilot;
import dev.blockpilot.platform.ClientPlatform;
import java.nio.file.Path;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.KeyMapping;

/** The mod on Fabric: installs the platform and forwards Fabric API's events to the platform hooks. */
public final class FabricClientEntrypoint implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientPlatform.install(new ClientPlatform.Services() {
            @Override
            public String loaderName() {
                return "fabric";
            }

            @Override
            public String modVersion() {
                return FabricLoader.getInstance().getModContainer(Blockpilot.MOD_ID)
                    .map(container -> container.getMetadata().getVersion().getFriendlyString())
                    .orElse("unknown");
            }

            @Override
            public String minecraftVersion() {
                return SharedConstants.getCurrentVersion().getName();
            }

            @Override
            public boolean isModLoaded(String modId) {
                return FabricLoader.getInstance().isModLoaded(modId);
            }

            @Override
            public String versionOf(String modId) {
                return FabricLoader.getInstance().getModContainer(modId)
                    .map(container -> container.getMetadata().getVersion().getFriendlyString())
                    .orElse(null);
            }

            @Override
            public Path configDir() {
                return FabricLoader.getInstance().getConfigDir();
            }

            @Override
            public Path gameDir() {
                return FabricLoader.getInstance().getGameDir();
            }

            @Override
            public void registerKeyMapping(KeyMapping mapping) {
                KeyBindingHelper.registerKeyBinding(mapping);
            }
        });
        Blockpilot.init();

        ClientLifecycleEvents.CLIENT_STARTED.register(client -> ClientPlatform.hooks().onLoadComplete());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> ClientPlatform.hooks().onClientStopping());
        ClientTickEvents.END_CLIENT_TICK.register(client -> ClientPlatform.hooks().onClientTickEnd());
        HudRenderCallback.EVENT.register((graphics, deltaTracker) -> ClientPlatform.hooks().onHudRender(graphics));
        ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, receptionTimestamp) ->
            ClientPlatform.hooks().onChatReceived(message));
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) {
                ClientPlatform.hooks().onChatReceived(message);
            }
        });
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> ClientPlatform.hooks().onScreenInit(screen));
        ServerTickEvents.END_SERVER_TICK.register(server -> ClientPlatform.hooks().onServerTickEnd(server));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> ClientPlatform.hooks().onServerStopping(server));
    }
}

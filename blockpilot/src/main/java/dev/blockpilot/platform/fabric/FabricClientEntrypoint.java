package dev.blockpilot.platform.fabric;

import dev.blockpilot.Blockpilot;
import dev.blockpilot.platform.ClientPlatform;
import dev.blockpilot.platform.ModConfigs;
import java.net.URISyntaxException;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Path;
import java.security.CodeSource;
//? if >=26.1 {
/*import java.util.HashSet;
*///?}
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
//? if >=26.1 {
/*import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
*///?} else {
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
//?}
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
//? if >=26.1 {
/*import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
*///?} else {
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
//?}
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModOrigin;
import net.minecraft.SharedConstants;
import net.minecraft.client.KeyMapping;
//? if >=26.1 {
/*import net.minecraft.resources.ResourceLocation;
*///?}

/** The mod on Fabric: installs the platform and forwards Fabric API's events to the platform hooks. */
public final class FabricClientEntrypoint implements ClientModInitializer {
    //? if >=26.1 {
    /*private final Set<KeyMapping.Category> categories = new HashSet<>();
    *///?}

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
            public List<Path> modPaths() {
                Set<Path> paths = new LinkedHashSet<>();
                // The origin is only where fabric.mod.json was found, which in a dev run is the resources folder;
                // the classes come from wherever these were loaded from.
                CodeSource classes = FabricClientEntrypoint.class.getProtectionDomain().getCodeSource();
                if (classes != null) {
                    try {
                        paths.add(Path.of(classes.getLocation().toURI()));
                    } catch (URISyntaxException | IllegalArgumentException | FileSystemNotFoundException e) {
                        // Not a plain file, as for a jar nested in another.
                    }
                }
                FabricLoader.getInstance().getModContainer(Blockpilot.MOD_ID)
                    .map(ModContainer::getOrigin)
                    .filter(origin -> origin.getKind() == ModOrigin.Kind.PATH)
                    .ifPresent(origin -> paths.addAll(origin.getPaths()));
                return List.copyOf(paths);
            }

            @Override
            public String modOf(Class<?> type) {
                String file = type.getName().replace('.', '/') + ".class";
                for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
                    if (mod.findPath(file).isPresent()) {
                        return mod.getMetadata().getId();
                    }
                }
                return null;
            }

            @Override
            public void registerKeyMapping(KeyMapping mapping) {
                //? if >=26.1 {
                /*// Categories are registered objects here, and Fabric leaves registering them to the mod.
                if (categories.add(mapping.getCategory())) {
                    KeyMapping.Category.register(mapping.getCategory().id());
                }
                KeyMappingHelper.registerKeyMapping(mapping);
                *///?} else {
                KeyBindingHelper.registerKeyBinding(mapping);
                //?}
            }

            @Override
            public ModConfigs modConfigs() {
                return ModConfigs.NONE;
            }
        });
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

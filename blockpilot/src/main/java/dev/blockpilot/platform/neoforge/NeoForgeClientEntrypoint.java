package dev.blockpilot.platform.neoforge;

import dev.blockpilot.Blockpilot;
import dev.blockpilot.platform.ClientPlatform;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.GameShuttingDownEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** The mod on NeoForge: installs the platform and forwards NeoForge's events to the platform hooks. */
@Mod(value = Blockpilot.MOD_ID, dist = Dist.CLIENT)
public final class NeoForgeClientEntrypoint {
    private final List<KeyMapping> keyMappings = new ArrayList<>();

    public NeoForgeClientEntrypoint(IEventBus modEventBus) {
        ClientPlatform.install(new ClientPlatform.Services() {
            @Override
            public String loaderName() {
                return "neoforge";
            }

            @Override
            public String modVersion() {
                return ModList.get().getModContainerById(Blockpilot.MOD_ID)
                    .map(container -> container.getModInfo().getVersion().toString())
                    .orElse("unknown");
            }

            @Override
            public String minecraftVersion() {
                return SharedConstants.getCurrentVersion().getName();
            }

            @Override
            public boolean isModLoaded(String modId) {
                return ModList.get().isLoaded(modId);
            }

            @Override
            public Path configDir() {
                return FMLPaths.CONFIGDIR.get();
            }

            @Override
            public Path gameDir() {
                return FMLPaths.GAMEDIR.get();
            }

            @Override
            public void registerKeyMapping(KeyMapping mapping) {
                keyMappings.add(mapping);
            }
        });
        Blockpilot.init();

        modEventBus.addListener(RegisterKeyMappingsEvent.class, event -> keyMappings.forEach(event::register));
        modEventBus.addListener(FMLLoadCompleteEvent.class, event -> ClientPlatform.hooks().onLoadComplete());

        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> ClientPlatform.hooks().onClientTickEnd());
        NeoForge.EVENT_BUS.addListener(RenderGuiEvent.Post.class,
            event -> ClientPlatform.hooks().onHudRender(event.getGuiGraphics()));
        NeoForge.EVENT_BUS.addListener(ClientChatReceivedEvent.class,
            event -> ClientPlatform.hooks().onChatReceived(event.getMessage()));
        NeoForge.EVENT_BUS.addListener(ScreenEvent.Init.Post.class,
            event -> ClientPlatform.hooks().onScreenInit(event.getScreen()));
        NeoForge.EVENT_BUS.addListener(GameShuttingDownEvent.class, event -> ClientPlatform.hooks().onClientStopping());
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class,
            event -> ClientPlatform.hooks().onServerTickEnd(event.getServer()));
        NeoForge.EVENT_BUS.addListener(ServerStoppingEvent.class,
            event -> ClientPlatform.hooks().onServerStopping(event.getServer()));
    }
}

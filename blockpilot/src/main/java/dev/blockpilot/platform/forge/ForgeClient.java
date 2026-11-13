package dev.blockpilot.platform.forge;

import dev.blockpilot.Blockpilot;
import dev.blockpilot.platform.ClientPlatform;
import java.util.function.Consumer;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.GameShuttingDownEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;

/** Installs the platform and forwards Forge's events to the platform hooks, as the NeoForge entry point does. */
final class ForgeClient {
    private ForgeClient() {
    }

    static void start(IEventBus modEventBus) {
        ForgeServices services = new ForgeServices();
        ClientPlatform.install(services);
        Blockpilot.init();

        listen(modEventBus, EventPriority.NORMAL, RegisterKeyMappingsEvent.class,
            event -> services.keyMappings().forEach(event::register));
        listen(modEventBus, EventPriority.NORMAL, FMLLoadCompleteEvent.class,
            event -> ClientPlatform.hooks().onLoadComplete());

        IEventBus bus = MinecraftForge.EVENT_BUS;
        listen(bus, EventPriority.NORMAL, TickEvent.ClientTickEvent.class, event -> {
            if (event.phase == TickEvent.Phase.END) {
                ClientPlatform.hooks().onClientTickEnd();
            }
        });
        // Between the first and the last listener of a stage run all its other handlers, which a capture times.
        listen(bus, EventPriority.HIGHEST, RenderLevelStageEvent.class,
            event -> ClientPlatform.hooks().onRenderStageStart());
        listen(bus, EventPriority.LOWEST, RenderLevelStageEvent.class,
            event -> ClientPlatform.hooks().onRenderStageEnd(event.getStage().toString().replace("minecraft:", "")));
        listen(bus, EventPriority.NORMAL, RenderGuiEvent.Post.class,
            event -> ClientPlatform.hooks().onHudRender(event.getGuiGraphics()));
        // Last, so the roll is the one every other mod's listener settled on.
        listen(bus, EventPriority.LOWEST, ViewportEvent.ComputeCameraAngles.class,
            event -> services.roll(event.getRoll()));
        // First, so that the window, its log line and every later listener get an untrusted player's line withheld.
        listen(bus, EventPriority.HIGHEST, ClientChatReceivedEvent.class, event -> {
            if (!event.isSystem()) {
                event.setMessage(ClientPlatform.hooks().judgeChat(event.getMessage(), event.getSender()));
            }
        });
        listen(bus, EventPriority.NORMAL, ClientChatReceivedEvent.class,
            event -> ClientPlatform.hooks().onChatReceived(event.getMessage()));
        listen(bus, EventPriority.NORMAL, ScreenEvent.Init.Post.class,
            event -> ClientPlatform.hooks().onScreenInit(event.getScreen()));
        listen(bus, EventPriority.NORMAL, GameShuttingDownEvent.class,
            event -> ClientPlatform.hooks().onClientStopping());
        listen(bus, EventPriority.NORMAL, ServerStartedEvent.class,
            event -> ClientPlatform.hooks().onServerStarted(event.getServer()));
        listen(bus, EventPriority.NORMAL, TickEvent.ServerTickEvent.class, event -> {
            if (event.phase == TickEvent.Phase.END) {
                ClientPlatform.hooks().onServerTickEnd(event.getServer());
            }
        });
        listen(bus, EventPriority.NORMAL, ServerStoppingEvent.class,
            event -> ClientPlatform.hooks().onServerStopping(event.getServer()));
        // New chunks come from NewChunkMixin on 1.20.1, as on Fabric.
        listen(bus, EventPriority.NORMAL, ChunkEvent.Load.class, event -> {
            if (!event.getLevel().isClientSide()) {
                ClientPlatform.hooks().onServerChunkLoaded();
            }
        });
    }

    /** With the event class named, as Forge's bus can't always read it off a lambda. */
    private static <T extends Event> void listen(IEventBus bus, EventPriority priority, Class<T> type,
        Consumer<T> listener) {
        bus.addListener(priority, false, type, listener);
    }
}

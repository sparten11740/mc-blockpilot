package dev.blockpilot.platform.neoforge;

import dev.blockpilot.Blockpilot;
import dev.blockpilot.platform.ClientPlatform;
//? if >=1.21.11 {
/*import java.util.List;
import java.util.Locale;
import net.minecraft.client.KeyMapping;
*///?}
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.GameShuttingDownEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** The mod on NeoForge: installs the platform and forwards NeoForge's events to the platform hooks. */
@Mod(value = Blockpilot.MOD_ID, dist = Dist.CLIENT)
public final class NeoForgeClientEntrypoint {
    public NeoForgeClientEntrypoint(IEventBus modEventBus) {
        NeoForgeServices services = new NeoForgeServices();
        ClientPlatform.install(services);
        Blockpilot.init();

        modEventBus.addListener(RegisterKeyMappingsEvent.class, event -> {
            //? if >=1.21.11 {
            /*services.keyMappings().stream().map(KeyMapping::getCategory).distinct().forEach(event::registerCategory);
            *///?}
            services.keyMappings().forEach(event::register);
        });
        modEventBus.addListener(FMLLoadCompleteEvent.class, event -> ClientPlatform.hooks().onLoadComplete());

        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> ClientPlatform.hooks().onClientTickEnd());
        // Between the first and the last listener of a stage run all its other handlers, which a capture times.
        //? if >=26.1 {
        /*List<Class<? extends RenderLevelStageEvent>> stages = List.of(RenderLevelStageEvent.AfterSky.class,
            RenderLevelStageEvent.AfterOpaqueBlocks.class, RenderLevelStageEvent.AfterOpaqueFeatures.class,
            RenderLevelStageEvent.AfterTranslucentFeatures.class, RenderLevelStageEvent.AfterTranslucentBlocks.class,
            RenderLevelStageEvent.AfterTranslucentParticles.class, RenderLevelStageEvent.AfterWeather.class,
            RenderLevelStageEvent.AfterLevel.class);
        *///?} else if >=1.21.11 {
        /*List<Class<? extends RenderLevelStageEvent>> stages = List.of(RenderLevelStageEvent.AfterSky.class,
            RenderLevelStageEvent.AfterOpaqueBlocks.class, RenderLevelStageEvent.AfterEntities.class,
            RenderLevelStageEvent.AfterTranslucentBlocks.class, RenderLevelStageEvent.AfterTripwireBlocks.class,
            RenderLevelStageEvent.AfterParticles.class, RenderLevelStageEvent.AfterWeather.class,
            RenderLevelStageEvent.AfterLevel.class);
        *///?}
        //? if >=1.21.11 {
        /*// Each stage is its own event class here, named after the stage (AfterSky is after_sky).
        stages.forEach(stage -> {
            String name = stage.getSimpleName().replaceAll("(?<=[a-z])(?=[A-Z])", "_").toLowerCase(Locale.ROOT);
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, stage,
                event -> ClientPlatform.hooks().onRenderStageStart());
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, stage,
                event -> ClientPlatform.hooks().onRenderStageEnd(name));
        });
        *///?} else {
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, RenderLevelStageEvent.class,
            event -> ClientPlatform.hooks().onRenderStageStart());
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, RenderLevelStageEvent.class,
            event -> ClientPlatform.hooks().onRenderStageEnd(event.getStage().toString().replace("minecraft:", "")));
        //?}
        NeoForge.EVENT_BUS.addListener(RenderGuiEvent.Post.class,
            event -> ClientPlatform.hooks().onHudRender(event.getGuiGraphics()));
        // First, so that the window, its log line and every later listener get an untrusted player's line withheld.
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, ClientChatReceivedEvent.class, event -> {
            if (!event.isSystem()) {
                event.setMessage(ClientPlatform.hooks().judgeChat(event.getMessage(), event.getSender()));
            }
        });
        NeoForge.EVENT_BUS.addListener(ClientChatReceivedEvent.class,
            event -> ClientPlatform.hooks().onChatReceived(event.getMessage()));
        NeoForge.EVENT_BUS.addListener(ScreenEvent.Init.Post.class,
            event -> ClientPlatform.hooks().onScreenInit(event.getScreen()));
        NeoForge.EVENT_BUS.addListener(GameShuttingDownEvent.class, event -> ClientPlatform.hooks().onClientStopping());
        NeoForge.EVENT_BUS.addListener(ServerStartedEvent.class,
            event -> ClientPlatform.hooks().onServerStarted(event.getServer()));
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class,
            event -> ClientPlatform.hooks().onServerTickEnd(event.getServer()));
        NeoForge.EVENT_BUS.addListener(ServerStoppingEvent.class,
            event -> ClientPlatform.hooks().onServerStopping(event.getServer()));
        NeoForge.EVENT_BUS.addListener(ChunkEvent.Load.class, event -> {
            if (!event.getLevel().isClientSide()) {
                ClientPlatform.hooks().onServerChunkLoaded();
                if (event.isNewChunk()) {
                    ClientPlatform.hooks().onServerChunkGenerated();
                }
            }
        });
    }
}

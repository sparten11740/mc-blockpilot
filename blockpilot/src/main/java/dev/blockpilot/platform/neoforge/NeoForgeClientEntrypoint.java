package dev.blockpilot.platform.neoforge;

import dev.blockpilot.Blockpilot;
import dev.blockpilot.game.perf.Perf;
import dev.blockpilot.platform.ClientPlatform;
import dev.blockpilot.platform.ModConfigs;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
//? if >=26.1 {
/*import java.util.Locale;
*///?}
import net.minecraft.SharedConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.neoforged.fml.loading.FMLPaths;
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
import net.neoforged.neoforgespi.language.IModFileInfo;

/** The mod on NeoForge: installs the platform and forwards NeoForge's events to the platform hooks. */
@Mod(value = Blockpilot.MOD_ID, dist = Dist.CLIENT)
public final class NeoForgeClientEntrypoint {
    private final List<KeyMapping> keyMappings = new ArrayList<>();
    private final ModConfigs configs = new NeoForgeModConfigs();

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
            public String versionOf(String modId) {
                return ModList.get().getModContainerById(modId)
                    .map(container -> container.getModInfo().getVersion().toString())
                    .orElse(null);
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
            public List<Path> modPaths() {
                IModFileInfo file = ModList.get().getModFileById(Blockpilot.MOD_ID);
                if (file == null) {
                    return List.of();
                }
                //? if >=26.1 {
                /*return List.copyOf(file.getFile().getContents().getContentRoots());
                *///?} else {
                // This FML names only the primary path: the jar, or in a dev run the classes folder.
                return List.of(file.getFile().getFilePath());
                //?}
            }

            @Override
            public String modOf(Class<?> type) {
                String file = type.getName().replace('.', '/') + ".class";
                for (IModFileInfo info : ModList.get().getModFiles()) {
                    //? if >=26.1 {
                    /*boolean holds = info.getFile().getContents().containsFile(file);
                    *///?} else {
                    boolean holds = Files.exists(info.getFile().findResource(file.split("/")));
                    //?}
                    if (holds && !info.getMods().isEmpty()) {
                        return info.getMods().get(0).getModId();
                    }
                }
                return null;
            }

            @Override
            public void registerKeyMapping(KeyMapping mapping) {
                keyMappings.add(mapping);
            }

            @Override
            public ModConfigs modConfigs() {
                return configs;
            }
        });
        Blockpilot.init();

        modEventBus.addListener(RegisterKeyMappingsEvent.class, event -> {
            //? if >=26.1 {
            /*keyMappings.stream().map(KeyMapping::getCategory).distinct().forEach(event::registerCategory);
            *///?}
            keyMappings.forEach(event::register);
        });
        modEventBus.addListener(FMLLoadCompleteEvent.class, event -> ClientPlatform.hooks().onLoadComplete());

        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> ClientPlatform.hooks().onClientTickEnd());
        // Between the first and the last listener of a stage run all its other handlers, which a capture times.
        //? if >=26.1 {
        /*// Each stage is its own event class here, named after the stage (AfterSky is after_sky).
        List.of(RenderLevelStageEvent.AfterSky.class, RenderLevelStageEvent.AfterOpaqueBlocks.class,
            RenderLevelStageEvent.AfterOpaqueFeatures.class, RenderLevelStageEvent.AfterTranslucentFeatures.class,
            RenderLevelStageEvent.AfterTranslucentBlocks.class, RenderLevelStageEvent.AfterTranslucentParticles.class,
            RenderLevelStageEvent.AfterWeather.class, RenderLevelStageEvent.AfterLevel.class).forEach(stage -> {
                String name = stage.getSimpleName().replaceAll("(?<=[a-z])(?=[A-Z])", "_").toLowerCase(Locale.ROOT);
                NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, stage, event -> Perf.stageStart());
                NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, stage, event -> Perf.stageEnd(name));
            });
        *///?} else {
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, RenderLevelStageEvent.class, event -> Perf.stageStart());
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, RenderLevelStageEvent.class,
            event -> Perf.stageEnd(event.getStage().toString().replace("minecraft:", "")));
        //?}
        NeoForge.EVENT_BUS.addListener(RenderGuiEvent.Post.class,
            event -> ClientPlatform.hooks().onHudRender(event.getGuiGraphics()));
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

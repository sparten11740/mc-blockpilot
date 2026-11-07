package dev.blockpilot.platform;

import java.nio.file.FileSystems;
import java.nio.file.Path;
//? if >=26.1 {
/*import java.util.HashSet;
*///?}
import java.util.List;
import java.util.Map;
import java.util.Objects;
//? if >=26.1 {
/*import java.util.Set;
*///?}
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.SharedConstants;
import net.minecraft.client.Camera;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.CreativeModeTab;

/**
 * What the mod needs from the mod loader it runs on. The loader's entry point (platform.neoforge or platform.fabric,
 * or on the plain client the agent's platform.vanilla; only one of them is in any jar) installs the implementation
 * before anything else runs, so the rest of the mod calls these without knowing which loader it is on.
 */
public final class ClientPlatform {
    /** The loader-specific half; see the entry points in platform.neoforge, platform.fabric and platform.vanilla. */
    public interface Services {
        String loaderName();

        /** This mod's version as the loader read it from the jar's metadata. */
        String modVersion();

        /** Null when no mod with that id is loaded. */
        String versionOf(String modId);

        Path configDir();

        Path gameDir();

        /** The jar, or in a dev run the folders, the loader loaded this mod from. */
        List<Path> modPaths();

        /** The id of the mod whose files hold the class (by its binary name), or null when none does. */
        String modOf(String className);

        /** Registers a key mapping; must be called while the mod is being constructed. */
        void registerKeyMapping(KeyMapping mapping);

        ModConfigs modConfigs();

        /** The tabs the creative inventory shows on its current page, in the page's order. */
        List<CreativeModeTab> creativeTabsOnPage(CreativeModeInventoryScreen screen);

        /** How far the camera is rolled, in degrees, which only NeoForge's events can do. */
        default float cameraRoll(Camera camera) {
            return 0;
        }

        /** Why the loader doesn't run GameTests, naming what turns them on, or null when it does. */
        default String gameTestsOff() {
            return null;
        }
    }

    /** Callbacks the loader's entry point forwards its events to. */
    public interface Hooks {
        default void onLoadComplete() {
        }

        default void onClientTickEnd() {
        }

        default void onHudRender(GuiGraphics graphics) {
        }

        /** {@code sender}: the player whose chat it is, or null for system and mod messages. */
        default void onChatReceived(Component message, UUID sender) {
        }

        default void onScreenInit(Screen screen) {
        }

        default void onClientStopping() {
        }

        /** The server has loaded its levels and not ticked yet; on the server thread. */
        default void onServerStarted(MinecraftServer server) {
        }

        default void onServerTickEnd(MinecraftServer server) {
        }

        default void onServerStopping(MinecraftServer server) {
        }

        /** A chunk became full in a server level, read from disk or generated; on the server thread. */
        default void onServerChunkLoaded() {
        }

        /** A newly generated chunk became full, after its {@link #onServerChunkLoaded}; on the server thread. */
        default void onServerChunkGenerated() {
        }

        /** Before a stage's handlers, on a loader that has render stages. */
        default void onRenderStageStart() {
        }

        /** After the handlers of the stage so named. */
        default void onRenderStageEnd(String stage) {
        }
    }

    private static Services services;
    //? if >=26.1 {
    /*private static final Set<KeyMapping.Category> CATEGORIES = new HashSet<>();
    *///?}
    /** {@link #modOf} per class name, "" for none; a class doesn't change mods. */
    private static final Map<String, String> MODS_OF = new ConcurrentHashMap<>();
    private static Hooks hooks = new Hooks() {
    };

    private ClientPlatform() {
    }

    public static void install(Services implementation) {
        services = implementation;
    }

    public static void setHooks(Hooks implementation) {
        hooks = implementation;
    }

    public static Hooks hooks() {
        return hooks;
    }

    private static Services services() {
        if (services == null) {
            throw new IllegalStateException("No platform installed; the loader's entry point has to install one first");
        }
        return services;
    }

    public static String loaderName() {
        return services().loaderName();
    }

    public static String modVersion() {
        return services().modVersion();
    }

    public static String minecraftVersion() {
        return SharedConstants.getCurrentVersion().getName();
    }

    public static boolean isModLoaded(String modId) {
        return versionOf(modId) != null;
    }

    public static String versionOf(String modId) {
        return services().versionOf(modId);
    }

    public static Path configDir() {
        return services().configDir();
    }

    public static Path gameDir() {
        return services().gameDir();
    }

    /** Only plain files and folders: a jar nested in another mod's jar has none. */
    public static List<Path> modPaths() {
        return services().modPaths().stream().filter(path -> path.getFileSystem() == FileSystems.getDefault()).toList();
    }

    /** The id of the mod whose files hold the class, or null when none does. */
    public static String modOf(Class<?> type) {
        return modOf(type.getName());
    }

    /** As {@link #modOf(Class)}, by the class's binary name, as a stack frame gives it. */
    public static String modOf(String className) {
        String mod = MODS_OF.computeIfAbsent(className, key -> Objects.requireNonNullElse(services().modOf(key), ""));
        return mod.isEmpty() ? null : mod;
    }

    public static void registerKeyMapping(KeyMapping mapping) {
        services().registerKeyMapping(mapping);
    }

    public static ModConfigs modConfigs() {
        return services().modConfigs();
    }

    public static List<CreativeModeTab> creativeTabsOnPage(CreativeModeInventoryScreen screen) {
        return services().creativeTabsOnPage(screen);
    }

    public static float cameraRoll(Camera camera) {
        return services().cameraRoll(camera);
    }

    public static String gameTestsOff() {
        return services().gameTestsOff();
    }

    // Categories are registered objects from 26.1. A loader that leaves that to the mod calls this for each mapping.
    //? if >=26.1 {
    /*public static void registerCategoryOf(KeyMapping mapping) {
        if (CATEGORIES.add(mapping.getCategory())) {
            KeyMapping.Category.register(mapping.getCategory().id());
        }
    }
    *///?}
}

package dev.blockpilot.platform;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.CreativeModeTab;

/**
 * What the mod needs from the mod loader it runs on. The loader's entry point (platform.neoforge or platform.fabric,
 * only one of which is in any jar) installs the implementation before anything else runs, so the rest of the mod
 * calls these without knowing which loader it is on.
 */
public final class ClientPlatform {
    /** The loader-specific half; see the entry points in platform.neoforge and platform.fabric. */
    public interface Services {
        String loaderName();

        /** This mod's version as the loader read it from the jar's metadata. */
        String modVersion();

        String minecraftVersion();

        boolean isModLoaded(String modId);

        /** Null when no mod with that id is loaded. */
        String versionOf(String modId);

        Path configDir();

        Path gameDir();

        /** The jar, or in a dev run the folders, the loader loaded this mod from. */
        List<Path> modPaths();

        /** The id of the mod whose files hold the class, or null when none does. */
        String modOf(Class<?> type);

        /** Registers a key mapping; must be called while the mod is being constructed. */
        void registerKeyMapping(KeyMapping mapping);

        ModConfigs modConfigs();

        /** The tabs the creative inventory shows on its current page, in the page's order. */
        List<CreativeModeTab> creativeTabsOnPage(CreativeModeInventoryScreen screen);
    }

    /** Callbacks the loader's entry point forwards its events to. */
    public interface Hooks {
        default void onLoadComplete() {
        }

        default void onClientTickEnd() {
        }

        default void onHudRender(GuiGraphics graphics) {
        }

        default void onChatReceived(Component message) {
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
    }

    private static Services services;
    /** {@link #modOf} per class, "" for none; a class doesn't change mods. */
    private static final Map<Class<?>, String> MODS_OF = new ConcurrentHashMap<>();
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
        return services().minecraftVersion();
    }

    public static boolean isModLoaded(String modId) {
        return services().isModLoaded(modId);
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
        String mod = MODS_OF.computeIfAbsent(type, key -> Objects.requireNonNullElse(services().modOf(key), ""));
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
}

package dev.blockpilot.platform;

import java.nio.file.Path;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

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

        Path configDir();

        Path gameDir();

        /** Registers a key mapping; must be called while the mod is being constructed. */
        void registerKeyMapping(KeyMapping mapping);
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

        default void onServerTickEnd(MinecraftServer server) {
        }

        default void onServerStopping(MinecraftServer server) {
        }
    }

    private static Services services;
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

    public static Path configDir() {
        return services().configDir();
    }

    public static Path gameDir() {
        return services().gameDir();
    }

    public static void registerKeyMapping(KeyMapping mapping) {
        services().registerKeyMapping(mapping);
    }
}

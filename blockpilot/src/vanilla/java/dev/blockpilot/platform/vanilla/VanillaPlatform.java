package dev.blockpilot.platform.vanilla;

import dev.blockpilot.Blockpilot;
import dev.blockpilot.platform.ClientPlatform;
import dev.blockpilot.platform.ModConfigs;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import net.minecraft.SharedConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.locale.Language;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;

/**
 * The mod on the plain client: installs the platform as the game starts. What a loader would do besides, the mixins
 * in {@code platform.vanilla.mixin} do: they forward the game's events to the platform hooks, add the key mappings to
 * the options and the translations to the language.
 */
public final class VanillaPlatform implements ClientPlatform.Services {
    private static final List<KeyMapping> KEY_MAPPINGS = new ArrayList<>();
    private static final Set<KeyMapping.Category> CATEGORIES = new HashSet<>();

    private final Path jar;
    private final String version;

    private VanillaPlatform(Path jar) {
        this.jar = jar;
        try (JarFile file = new JarFile(jar.toFile())) {
            version = file.getManifest().getMainAttributes().getValue(Attributes.Name.IMPLEMENTATION_VERSION);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the agent's manifest in " + jar, e);
        }
    }

    /** Called as the game is constructed, before its options load. */
    public static void start() {
        ClientPlatform.install(new VanillaPlatform(AgentBootstrap.jar()));
        Blockpilot.init();
    }

    /** The options' key mappings with the mod's added, for the options to load their keys into. */
    public static KeyMapping[] withKeyMappings(KeyMapping[] mappings) {
        List<KeyMapping> all = new ArrayList<>(List.of(mappings));
        KEY_MAPPINGS.stream().filter(mapping -> !all.contains(mapping)).forEach(all::add);
        return all.toArray(KeyMapping[]::new);
    }

    /**
     * Adds the mod's translations for the loading languages, most specific first. Vanilla only reads them from
     * resource packs, and what the packs give wins, as it would over a loader's mod resources.
     */
    public static void addTranslations(List<String> languages, Map<String, String> translations) {
        for (String language : languages.reversed()) {
            String path = "/assets/" + Blockpilot.MOD_ID + "/lang/" + language + ".json";
            try (InputStream file = VanillaPlatform.class.getResourceAsStream(path)) {
                if (file != null) {
                    Language.loadFromJson(file, translations::putIfAbsent);
                }
            } catch (IOException | RuntimeException e) {
                Blockpilot.LOGGER.warn("Could not load Blockpilot's translations from {}", path, e);
            }
        }
    }

    @Override
    public String loaderName() {
        return "vanilla";
    }

    @Override
    public String modVersion() {
        return version;
    }

    @Override
    public String minecraftVersion() {
        return SharedConstants.getCurrentVersion().name();
    }

    @Override
    public boolean isModLoaded(String modId) {
        return versionOf(modId) != null;
    }

    /** Only this mod and the game: there is nothing to load others. */
    @Override
    public String versionOf(String modId) {
        return switch (modId) {
            case Blockpilot.MOD_ID -> modVersion();
            case "minecraft" -> minecraftVersion();
            default -> null;
        };
    }

    @Override
    public Path configDir() {
        return gameDir().resolve("config");
    }

    @Override
    public Path gameDir() {
        return Minecraft.getInstance().gameDirectory.toPath();
    }

    /** The agent jar, also in a dev run, where the classes come from the build folders and the jar from -javaagent. */
    @Override
    public List<Path> modPaths() {
        return List.of(jar);
    }

    @Override
    public String modOf(String className) {
        if (className.startsWith("dev.blockpilot.")) {
            return Blockpilot.MOD_ID;
        }
        return className.startsWith("net.minecraft.") || className.startsWith("com.mojang.") ? "minecraft" : null;
    }

    @Override
    public void registerKeyMapping(KeyMapping mapping) {
        // Categories are registered objects, which a loader would register for the mod.
        if (CATEGORIES.add(mapping.getCategory())) {
            KeyMapping.Category.register(mapping.getCategory().id());
        }
        KEY_MAPPINGS.add(mapping);
    }

    @Override
    public ModConfigs modConfigs() {
        return ModConfigs.NONE;
    }

    /** Vanilla has one page, with every tab that displays. */
    @Override
    public List<CreativeModeTab> creativeTabsOnPage(CreativeModeInventoryScreen screen) {
        return CreativeModeTabs.tabs();
    }
}

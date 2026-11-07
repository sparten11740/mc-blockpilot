package dev.blockpilot.platform.neoforge;

import dev.blockpilot.Blockpilot;
import dev.blockpilot.platform.ClientPlatform;
import dev.blockpilot.platform.ModConfigs;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.gametest.GameTestHooks;
import net.neoforged.neoforgespi.language.IModFileInfo;

/** What NeoForge answers for the mod. */
final class NeoForgeServices implements ClientPlatform.Services {
    private final List<KeyMapping> keyMappings = new ArrayList<>();
    private final ModConfigs configs = new NeoForgeModConfigs();

    /** The mappings to register once NeoForge asks for them. */
    List<KeyMapping> keyMappings() {
        return keyMappings;
    }

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
    public boolean developmentEnvironment() {
        //? if >=26.1 {
        /*return !FMLEnvironment.isProduction();
        *///?} else {
        return !FMLEnvironment.production;
        //?}
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
    public String modOf(String className) {
        String file = className.replace('.', '/') + ".class";
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

    @Override
    public List<CreativeModeTab> creativeTabsOnPage(CreativeModeInventoryScreen screen) {
        return screen.getCurrentPage().getVisibleTabs();
    }

    @Override
    public float cameraRoll(Camera camera) {
        return camera.getRoll();
    }

    @Override
    public String gameTestsOff() {
        return GameTestHooks.isGametestEnabled() ? null
            : "NeoForge runs GameTests only in a development environment started with -Dneoforge.enableGameTest=true";
    }
}

package dev.blockpilot.platform.forge;

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
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.forgespi.language.IModFileInfo;
import net.minecraftforge.gametest.ForgeGameTestHooks;

/** What Forge answers for the mod. */
final class ForgeServices implements ClientPlatform.Services {
    private final List<KeyMapping> keyMappings = new ArrayList<>();
    private final ModConfigs configs = new ForgeModConfigs();
    private volatile float roll;

    /** The mappings to register once Forge asks for them. */
    List<KeyMapping> keyMappings() {
        return keyMappings;
    }

    /** The roll Forge's camera event settled on this frame; Forge's camera keeps none of its own. */
    void roll(float degrees) {
        roll = degrees;
    }

    @Override
    public String loaderName() {
        return "forge";
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
        return !FMLEnvironment.production;
    }

    @Override
    public Path gameDir() {
        return FMLPaths.GAMEDIR.get();
    }

    @Override
    public List<Path> modPaths() {
        IModFileInfo file = ModList.get().getModFileById(Blockpilot.MOD_ID);
        // FML names only the primary path: the jar, or in a dev run the classes folder.
        return file == null ? List.of() : List.of(file.getFile().getFilePath());
    }

    @Override
    public String modOf(String className) {
        String[] path = (className.replace('.', '/') + ".class").split("/");
        for (IModFileInfo info : ModList.get().getModFiles()) {
            if (Files.exists(info.getFile().findResource(path)) && !info.getMods().isEmpty()) {
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
        return roll;
    }

    @Override
    public String gameTestsOff() {
        return ForgeGameTestHooks.isGametestEnabled() ? null
            : "Forge runs GameTests only in a development environment started with -Dforge.enableGameTest=true";
    }
}

package dev.blockpilot.platform.fabric;

import dev.blockpilot.Blockpilot;
import dev.blockpilot.platform.ClientPlatform;
import dev.blockpilot.platform.ModConfigs;
import java.net.URISyntaxException;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
//? if >=26.1 {
/*import net.fabricmc.fabric.api.client.creativetab.v1.FabricCreativeModeInventoryScreen;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
*///?} else if >=1.21 {
import net.fabricmc.fabric.api.client.itemgroup.v1.FabricCreativeInventoryScreen;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
//?} else {
/*import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.impl.client.itemgroup.CreativeGuiExtensions;
import net.fabricmc.fabric.impl.client.itemgroup.FabricCreativeGuiComponents;
import net.fabricmc.fabric.impl.itemgroup.FabricItemGroup;
import net.minecraft.world.item.CreativeModeTabs;
*///?}
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModOrigin;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.item.CreativeModeTab;

/** What Fabric's loader and API answer for the mod. */
final class FabricServices implements ClientPlatform.Services {
    @Override
    public String loaderName() {
        return "fabric";
    }

    @Override
    public String modVersion() {
        return FabricLoader.getInstance().getModContainer(Blockpilot.MOD_ID)
            .map(container -> container.getMetadata().getVersion().getFriendlyString())
            .orElse("unknown");
    }

    @Override
    public String versionOf(String modId) {
        return FabricLoader.getInstance().getModContainer(modId)
            .map(container -> container.getMetadata().getVersion().getFriendlyString())
            .orElse(null);
    }

    @Override
    public Path configDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    @Override
    public boolean developmentEnvironment() {
        return FabricLoader.getInstance().isDevelopmentEnvironment();
    }

    @Override
    public Path gameDir() {
        return FabricLoader.getInstance().getGameDir();
    }

    @Override
    public List<Path> modPaths() {
        Set<Path> paths = new LinkedHashSet<>();
        // The origin is only where fabric.mod.json was found, which in a dev run is the resources folder;
        // the classes come from wherever these were loaded from.
        CodeSource classes = FabricServices.class.getProtectionDomain().getCodeSource();
        if (classes != null) {
            try {
                paths.add(Path.of(classes.getLocation().toURI()));
            } catch (URISyntaxException | IllegalArgumentException | FileSystemNotFoundException e) {
                // Not a plain file, as for a jar nested in another.
            }
        }
        FabricLoader.getInstance().getModContainer(Blockpilot.MOD_ID)
            .map(ModContainer::getOrigin)
            .filter(origin -> origin.getKind() == ModOrigin.Kind.PATH)
            .ifPresent(origin -> paths.addAll(origin.getPaths()));
        return List.copyOf(paths);
    }

    @Override
    public String modOf(String className) {
        String file = className.replace('.', '/') + ".class";
        for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
            if (mod.findPath(file).isPresent()) {
                return mod.getMetadata().getId();
            }
        }
        return null;
    }

    @Override
    public void registerKeyMapping(KeyMapping mapping) {
        //? if >=26.1 {
        /*ClientPlatform.registerCategoryOf(mapping);
        KeyMappingHelper.registerKeyMapping(mapping);
        *///?} else {
        KeyBindingHelper.registerKeyBinding(mapping);
        //?}
    }

    @Override
    public ModConfigs modConfigs() {
        return ModConfigs.NONE;
    }

    @Override
    public List<CreativeModeTab> creativeTabsOnPage(CreativeModeInventoryScreen screen) {
        //? if >=26.1 {
        /*FabricCreativeModeInventoryScreen pages = (FabricCreativeModeInventoryScreen) screen;
        return pages.getTabsOnPage(pages.getCurrentPage());
        *///?} else if >=1.21 {
        FabricCreativeInventoryScreen pages = (FabricCreativeInventoryScreen) screen;
        return pages.getItemGroupsOnPage(pages.getCurrentPage());
        //?} else {
        /*// Fabric API 0.92 keeps the pages in its implementation: the tabs on every page, and each other tab's page.
        int page = ((CreativeGuiExtensions) screen).fabric_currentPage();
        return CreativeModeTabs.tabs().stream().filter(tab -> FabricCreativeGuiComponents.COMMON_GROUPS.contains(tab)
            || ((FabricItemGroup) tab).getPage() == page).toList();
        *///?}
    }
}

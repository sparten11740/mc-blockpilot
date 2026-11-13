package dev.blockpilot.platform.forge;

import dev.blockpilot.Blockpilot;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

/**
 * The mod on Forge 1.20.1. Its constructor takes no arguments, as NeoForge 47.1, which loads Forge 1.20.1 mods, can't
 * pass any. On a dedicated server the mod does nothing: Blockpilot is client-side, and its wiring lives in
 * {@link ForgeClient} so that no client class loads there.
 */
@Mod(Blockpilot.MOD_ID)
public final class ForgeClientEntrypoint {
    public ForgeClientEntrypoint() {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ForgeClient.start(FMLJavaModLoadingContext.get().getModEventBus());
        }
    }
}

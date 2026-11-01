package dev.blockpilot.platform.vanilla.mixin;

import dev.blockpilot.platform.ClientPlatform;
import dev.blockpilot.platform.vanilla.VanillaPlatform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.main.GameConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Starts the mod as the game is built, and forwards the client's start, ticks and stop. */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    // Where a loader starts its mods: the game folder is known, and the options, which read the key mappings, are next.
    @Inject(method = "<init>", at = @At(value = "NEW", target = "net/minecraft/client/Options"))
    private void blockpilot$start(GameConfig gameConfig, CallbackInfo info) {
        VanillaPlatform.start();
    }

    @Inject(method = "run", at = @At("HEAD"))
    private void blockpilot$started(CallbackInfo info) {
        ClientPlatform.hooks().onLoadComplete();
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void blockpilot$tickEnd(CallbackInfo info) {
        ClientPlatform.hooks().onClientTickEnd();
    }

    @Inject(method = "destroy", at = @At("HEAD"))
    private void blockpilot$stopping(CallbackInfo info) {
        ClientPlatform.hooks().onClientStopping();
    }
}
